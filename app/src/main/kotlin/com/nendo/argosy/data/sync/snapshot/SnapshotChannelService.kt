package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SnapshotChannelDao
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMChannelCreate
import com.nendo.argosy.data.remote.romm.RomMChannelUpdate
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotUpdate
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SnapshotChannelEntry(val channel: RomMChannel, val olderClientSaves: List<RomMSave>)

data class SnapshotLibrary(
    val romFileId: Long,
    val mine: List<SnapshotChannelEntry>,
    val community: List<SnapshotChannelEntry>,
    val backups: List<RomMSave>,
    val deviceChannelId: String?
)

sealed class SnapshotActionResult {
    data object Done : SnapshotActionResult()
    data object Stale : SnapshotActionResult()
    data object HardcoreDowngrade : SnapshotActionResult()
    data object Offline : SnapshotActionResult()
    data class Failed(val reason: String) : SnapshotActionResult()
}

/**
 * The channel view's data and actions on RomM's snapshot API, as RomM's web UI applies them:
 * restores, forks and copies are manifest-only pushes naming a parent, and a legacy save or a
 * backup becomes a snapshot through `copy_of`.
 */
@Singleton
class SnapshotChannelService @Inject constructor(
    private val gameDao: GameDao,
    private val channelDao: SnapshotChannelDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val apiClient: Lazy<SaveSyncApiClient>,
    private val engine: SnapshotSyncEngine,
    private val pusher: SnapshotPusher,
    private val fileResolver: SnapshotFileResolver
) {
    suspend fun isAvailable(gameId: Long): Boolean = engine.isEligible(gameId)

    suspend fun load(gameId: Long): SnapshotLibrary? = withContext(Dispatchers.IO) {
        val api = apiClient.get().getApi() ?: return@withContext null
        val game = gameDao.getById(gameId) ?: return@withContext null
        val rommId = game.rommId ?: return@withContext null
        val file = fileResolver.launchedFile(api, game) ?: return@withContext null
        val channels = runCatching { api.listChannels(listOf(file.id)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return@withContext null
        val saves = runCatching { api.getSavesByRom(rommId) }.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty()
        val ownSaves = saves.filter { save -> channels.none { it.id == save.channelId && !it.isOwn } }
        fun entry(channel: RomMChannel) = SnapshotChannelEntry(
            channel,
            ownSaves.filter { it.channelId == channel.id }.sortedByDescending { it.updatedAt }
        )
        SnapshotLibrary(
            romFileId = file.id,
            mine = channels.filter { it.isOwn }.map(::entry),
            community = channels.filter { !it.isOwn }.map { SnapshotChannelEntry(it, emptyList()) },
            backups = ownSaves.filter { it.channelId == null }.sortedByDescending { it.updatedAt },
            deviceChannelId = deviceChannelId(gameId, file.id, channels)
        )
    }

    suspend fun history(channelId: String, before: Long?): List<RomMSnapshot>? = withContext(Dispatchers.IO) {
        val api = apiClient.get().getApi() ?: return@withContext null
        runCatching { api.listSnapshotHistory(channelId, HISTORY_PAGE, before?.toString()) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()
    }

    suspend fun restoreAsCurrent(gameId: Long, emulatorId: String, channel: RomMChannel, snapshotId: Long): SnapshotActionResult {
        val result = push(intoChannel(channel).put("parent_snapshot_id", snapshotId))
        if (result == SnapshotActionResult.Done && isDeviceChannel(gameId, channel)) {
            engine.keepServer(gameId, emulatorId, argosyChannelOf(channel))
        }
        return result
    }

    suspend fun fork(romFileId: Long, snapshotId: Long, label: String): SnapshotActionResult =
        push(
            JSONObject()
                .put("rom_file_id", romFileId)
                .put("label", label)
                .put("expected_current_id", JSONObject.NULL)
                .put("parent_snapshot_id", snapshotId)
        )

    suspend fun copyOver(snapshotId: Long, target: RomMChannel): SnapshotActionResult =
        push(intoChannel(target).put("parent_snapshot_id", snapshotId))

    suspend fun makeSnapshot(channel: RomMChannel, saveId: Long): SnapshotActionResult =
        push(intoChannel(channel).put("save", JSONObject().put("copy_of", saveId)))

    suspend fun newChannel(romFileId: Long, label: String, fromBackupId: Long?): SnapshotActionResult {
        val api = apiClient.get().getApi() ?: return SnapshotActionResult.Offline
        val channel = runCatching { api.postChannel(RomMChannelCreate(romFileId, label)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()
            ?: return SnapshotActionResult.Failed("could not create channel $label")
        return fromBackupId?.let { makeSnapshot(channel, it) } ?: SnapshotActionResult.Done
    }

    suspend fun setPinned(snapshotId: Long, pinned: Boolean): SnapshotActionResult =
        call { it.updateSnapshot(snapshotId, RomMSnapshotUpdate(isPinned = pinned)).isSuccessful }

    suspend fun rename(channelId: String, label: String): SnapshotActionResult =
        call { it.updateChannel(channelId, RomMChannelUpdate(label = label)).isSuccessful }

    suspend fun setShared(channelId: String, shared: Boolean): SnapshotActionResult =
        call { it.updateChannel(channelId, RomMChannelUpdate(isPublic = shared)).isSuccessful }

    suspend fun delete(channelId: String): SnapshotActionResult =
        call { it.deleteChannel(channelId).isSuccessful }

    fun argosyChannelOf(channel: RomMChannel): String? =
        channel.label.takeUnless { it.equals(DEFAULT_LABEL, ignoreCase = true) }

    suspend fun rememberDeviceChannel(gameId: Long, argosyChannel: String?, channelId: String, romFileId: Long) {
        channelDao.upsert(
            SnapshotChannelEntity(
                ownerUserId = ownerUserId(),
                gameId = gameId,
                label = labelKeyOf(argosyChannel),
                channelId = channelId,
                romFileId = romFileId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun intoChannel(channel: RomMChannel): JSONObject =
        JSONObject()
            .put("rom_file_id", channel.romFileId)
            .put("channel_id", channel.id)
            .put("expected_current_id", channel.currentSnapshotId ?: JSONObject.NULL)

    private suspend fun push(manifest: JSONObject): SnapshotActionResult {
        val client = apiClient.get()
        val api = client.getApi() ?: return SnapshotActionResult.Offline
        val deviceId = client.getDeviceId() ?: return SnapshotActionResult.Offline
        manifest.put("emulator", EMULATOR).put("emulator_version", BuildConfig.VERSION_NAME)
        return when (val outcome = pusher.push(api, deviceId, manifest)) {
            is PushOutcome.Written -> SnapshotActionResult.Done
            is PushOutcome.Conflict -> SnapshotActionResult.Stale
            PushOutcome.HardcoreDowngrade -> SnapshotActionResult.HardcoreDowngrade
            is PushOutcome.Failed -> SnapshotActionResult.Failed(outcome.reason)
        }
    }

    private suspend fun call(block: suspend (RomMApi) -> Boolean): SnapshotActionResult {
        val api = apiClient.get().getApi() ?: return SnapshotActionResult.Offline
        return if (runCatching { block(api) }.getOrDefault(false)) SnapshotActionResult.Done
        else SnapshotActionResult.Failed("the server refused the change")
    }

    private suspend fun deviceChannelId(gameId: Long, romFileId: Long, channels: List<RomMChannel>): String? {
        val label = labelKeyOf(activeSaveRepository.getActiveChannel(gameId))
        channelDao.get(ownerUserId(), gameId, label)
            ?.takeIf { stored -> stored.romFileId == romFileId && channels.any { it.id == stored.channelId } }
            ?.let { return it.channelId }
        return channels.filter { it.isOwn && it.label.equals(label, ignoreCase = true) }
            .maxByOrNull { it.current?.createdAt.orEmpty() }?.id
    }

    private suspend fun isDeviceChannel(gameId: Long, channel: RomMChannel): Boolean {
        val stored = channelDao.get(ownerUserId(), gameId, labelKeyOf(activeSaveRepository.getActiveChannel(gameId)))
        return stored?.channelId == channel.id ||
            (stored == null && channel.label.equals(labelKeyOf(activeSaveRepository.getActiveChannel(gameId)), ignoreCase = true))
    }

    private fun labelKeyOf(argosyChannel: String?): String =
        SaveSyncApiClient.namedChannelOrNull(argosyChannel) ?: DEFAULT_LABEL

    private suspend fun ownerUserId(): Long = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER

    private companion object {
        const val HISTORY_PAGE = 20
        const val DEFAULT_LABEL = "default"
        const val EMULATOR = "argosy"
    }
}
