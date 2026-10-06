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
import com.nendo.argosy.hardware.SaveScreenshotCapture
import com.nendo.argosy.util.Logger
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

enum class SnapshotFailure {
    OFFLINE,
    REFUSED,
    NOT_FOUND,
    CONFLICT,
    UNKNOWN;

    companion object {
        fun ofStatus(code: Int): SnapshotFailure = when (code) {
            404 -> NOT_FOUND
            409 -> CONFLICT
            in 400..499 -> REFUSED
            else -> UNKNOWN
        }
    }
}

sealed class SnapshotActionResult {
    data object Done : SnapshotActionResult()
    data object Stale : SnapshotActionResult()
    data object HardcoreDowngrade : SnapshotActionResult()
    data object Offline : SnapshotActionResult()
    data class Failed(val failure: SnapshotFailure) : SnapshotActionResult()
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
    private val fileResolver: SnapshotFileResolver,
    private val saveScreenshots: SaveScreenshotCapture
) {
    suspend fun isAvailable(gameId: Long): Boolean = engine.isEligible(gameId)

    suspend fun load(gameId: Long): SnapshotLibrary? = withContext(Dispatchers.IO) {
        val api = apiClient.get().getApi() ?: return@withContext null
        val game = gameDao.getById(gameId) ?: return@withContext null
        val rommId = game.rommId ?: return@withContext null
        val file = fileResolver.launchedFile(api, game) ?: return@withContext null
        val channels = runCatching { api.listChannels(listOf(file.id)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return@withContext null
        val saves = runCatching { api.getSavesByRom(rommId) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
            ?: return@withContext null
        val ownSaves = saves.filter { save -> channels.none { it.id == save.channelId && !it.isOwn } }
        fun entry(channel: RomMChannel) = SnapshotChannelEntry(
            channel,
            ownSaves.filter { it.channelId == channel.id && it.slot != null }.sortedByDescending { it.updatedAt }
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

    suspend fun restoreAsCurrent(
        gameId: Long,
        emulatorId: String,
        channel: RomMChannel,
        snapshotId: Long,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotActionResult {
        val result = push(intoChannel(channel).put(PARENT_KEY, snapshotId), approveHardcoreDowngrade)
        if (result == SnapshotActionResult.Done && isDeviceChannel(gameId, channel)) {
            engine.keepServer(gameId, emulatorId, argosyChannelOf(channel))
        }
        return result
    }

    suspend fun fork(
        romFileId: Long,
        snapshotId: Long,
        label: String,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotActionResult =
        push(
            JSONObject()
                .put("rom_file_id", romFileId)
                .put("label", label)
                .put("expected_current_id", JSONObject.NULL)
                .put(PARENT_KEY, snapshotId),
            approveHardcoreDowngrade
        )

    suspend fun copyOver(
        snapshotId: Long,
        target: RomMChannel,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotActionResult =
        push(intoChannel(target).put(PARENT_KEY, snapshotId), approveHardcoreDowngrade)

    suspend fun makeSnapshot(
        channel: RomMChannel,
        saveId: Long,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotActionResult =
        push(intoChannel(channel).put("save", JSONObject().put("copy_of", saveId)), approveHardcoreDowngrade)

    suspend fun newChannel(romFileId: Long, label: String, fromBackupId: Long?): SnapshotActionResult {
        val api = apiClient.get().getApi() ?: return SnapshotActionResult.Offline
        val response = runCatching { api.postChannel(RomMChannelCreate(romFileId, label)) }.getOrElse {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT | creating channel $label failed: ${it.message}")
            return SnapshotActionResult.Failed(SnapshotFailure.OFFLINE)
        }
        val channel = response.takeIf { it.isSuccessful }?.body() ?: run {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT | creating channel $label answered ${response.code()}")
            return SnapshotActionResult.Failed(SnapshotFailure.ofStatus(response.code()))
        }
        return fromBackupId?.let { makeSnapshot(channel, it) } ?: SnapshotActionResult.Done
    }

    suspend fun setPinned(snapshotId: Long, pinned: Boolean): SnapshotActionResult =
        call("pin #$snapshotId") { it.updateSnapshot(snapshotId, RomMSnapshotUpdate(isPinned = pinned)).code() }

    suspend fun rename(channelId: String, label: String): SnapshotActionResult =
        call("rename $channelId") { it.updateChannel(channelId, RomMChannelUpdate(label = label)).code() }

    suspend fun setShared(channelId: String, shared: Boolean): SnapshotActionResult =
        call("share $channelId") { it.updateChannel(channelId, RomMChannelUpdate(isPublic = shared)).code() }

    suspend fun delete(channelId: String): SnapshotActionResult =
        call("delete $channelId") { it.deleteChannel(channelId).code() }

    fun argosyChannelOf(channel: RomMChannel): String? = SnapshotChannels.argosyChannelOf(channel.label)

    suspend fun rememberDeviceChannel(gameId: Long, argosyChannel: String?, channelId: String, romFileId: Long) {
        channelDao.upsert(
            SnapshotChannelEntity(
                ownerUserId = ownerUserId(),
                gameId = gameId,
                label = SnapshotChannels.labelOf(argosyChannel),
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

    private suspend fun push(manifest: JSONObject, approveHardcoreDowngrade: Boolean): SnapshotActionResult {
        val client = apiClient.get()
        val api = client.getApi() ?: return SnapshotActionResult.Offline
        val deviceId = client.getDeviceId() ?: return SnapshotActionResult.Offline
        manifest.put("emulator", SnapshotChannels.EMULATOR).put("emulator_version", BuildConfig.VERSION_NAME)
        if (approveHardcoreDowngrade) manifest.put("approve_hardcore_downgrade", true)
        return when (val outcome = pusher.push(api, deviceId, manifest)) {
            is PushOutcome.Written -> {
                manifest.optLong(PARENT_KEY, NO_PARENT).takeIf { it != NO_PARENT }
                    ?.let { saveScreenshots.carrySnapshotThumb(it, outcome.snapshot.id) }
                SnapshotActionResult.Done
            }
            is PushOutcome.Conflict -> SnapshotActionResult.Stale
            PushOutcome.HardcoreDowngrade -> SnapshotActionResult.HardcoreDowngrade
            is PushOutcome.Failed -> {
                Logger.warn(TAG, "[SaveSync] SNAPSHOT | channel push failed: ${outcome.reason}")
                SnapshotActionResult.Failed(outcome.failure)
            }
        }
    }

    private suspend fun call(action: String, block: suspend (RomMApi) -> Int): SnapshotActionResult {
        val api = apiClient.get().getApi() ?: return SnapshotActionResult.Offline
        val code = runCatching { block(api) }.getOrElse {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT | $action failed: ${it.message}")
            return SnapshotActionResult.Failed(SnapshotFailure.OFFLINE)
        }
        if (code in 200..299) return SnapshotActionResult.Done
        Logger.warn(TAG, "[SaveSync] SNAPSHOT | $action answered $code")
        return SnapshotActionResult.Failed(SnapshotFailure.ofStatus(code))
    }

    private suspend fun deviceChannelId(gameId: Long, romFileId: Long, channels: List<RomMChannel>): String? {
        val label = SnapshotChannels.labelOf(activeSaveRepository.getActiveChannel(gameId))
        channelDao.get(ownerUserId(), gameId, label)
            ?.takeIf { stored -> stored.romFileId == romFileId && channels.any { it.id == stored.channelId } }
            ?.let { return it.channelId }
        return channels.filter { it.isOwn && it.label.equals(label, ignoreCase = true) }
            .maxByOrNull { it.current?.createdAt.orEmpty() }?.id
    }

    private suspend fun isDeviceChannel(gameId: Long, channel: RomMChannel): Boolean {
        val label = SnapshotChannels.labelOf(activeSaveRepository.getActiveChannel(gameId))
        val stored = channelDao.get(ownerUserId(), gameId, label)
        return stored?.channelId == channel.id || (stored == null && channel.label.equals(label, ignoreCase = true))
    }

    private suspend fun ownerUserId(): Long = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER

    companion object {
        const val HISTORY_PAGE = 20
        private const val TAG = "SnapshotChannelService"
        private const val PARENT_KEY = "parent_snapshot_id"
        private const val NO_PARENT = -1L
    }
}
