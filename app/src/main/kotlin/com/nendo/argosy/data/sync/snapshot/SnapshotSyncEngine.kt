package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SnapshotChannelDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotConflict
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SnapshotSyncResult {
    data object NotEligible : SnapshotSyncResult()
    data object NoConnection : SnapshotSyncResult()
    data object UpToDate : SnapshotSyncResult()
    data class Pushed(val snapshotId: Long) : SnapshotSyncResult()
    data class Applied(val snapshotId: Long) : SnapshotSyncResult()
    data class Conflict(val current: RomMSnapshot?, val currentId: Long) : SnapshotSyncResult()
    data class HardcoreDowngrade(val currentId: Long?) : SnapshotSyncResult()
    data class Failed(val reason: String) : SnapshotSyncResult()
}

/**
 * Save sync against RomM's snapshot API (5.5+) for the games Sigil collects: list the channel's
 * current, decide on the device, then push or download. States are never listed in a push, so
 * every snapshot carries its parent's bank.
 */
@Singleton
class SnapshotSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val channelDao: SnapshotChannelDao,
    private val saveCacheDao: SaveCacheDao,
    private val sigilSaveHandler: SigilSaveHandler,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val apiClient: Lazy<SaveSyncApiClient>,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    moshi: Moshi
) {
    private val snapshotAdapter = moshi.adapter(RomMSnapshot::class.java)
    private val conflictAdapter = moshi.adapter(RomMSnapshotConflict::class.java)
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private class ChannelView(
        val api: RomMApi,
        val deviceId: String,
        val ownerUserId: Long,
        val game: GameEntity,
        val file: RomMRomFile,
        val stored: SnapshotChannelEntity?,
        val current: RomMSnapshot?,
        val channelId: String,
        val isNewChannel: Boolean,
        val joinsLegacyChannel: Boolean = false
    ) {
        fun asNewChannel(): ChannelView = ChannelView(
            api, deviceId, ownerUserId, game, file, null, null, UUID.randomUUID().toString(), isNewChannel = true
        )
    }

    suspend fun isEligible(gameId: Long, emulatorId: String): Boolean =
        apiClient.get().getCapabilities().supportsSnapshots &&
            sigilSaveHandler.route(gameId, emulatorId) != null

    suspend fun sync(gameId: Long, emulatorId: String, isHardcore: Boolean = false): SnapshotSyncResult =
        locked(gameId) {
            val ctx = load(gameId, emulatorId) ?: return@locked notReady(gameId, emulatorId)
            val local = when (val collected = sigilSaveHandler.collect(gameId, emulatorId)) {
                is SigilCollect.Found -> collected
                SigilCollect.Absent -> null
                SigilCollect.NotRouted -> return@locked SnapshotSyncResult.NotEligible
                is SigilCollect.Unreadable -> return@locked SnapshotSyncResult.Failed(collected.reason)
            }
            val action = SnapshotDecision.decide(held(ctx), currentPoint(ctx.current), local?.let { SaveHashes(it.contentHash, it.identityHash) })
            Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=$gameId channel=${ctx.channelId} held=${ctx.stored?.heldSnapshotId} current=${ctx.current?.id} | $action")
            when (action) {
                SnapshotAction.Nothing -> SnapshotSyncResult.UpToDate
                is SnapshotAction.Push -> push(ctx, local ?: return@locked SnapshotSyncResult.UpToDate, action.expectedCurrentId, isHardcore, false)
                is SnapshotAction.Adopt -> adopt(ctx, ctx.current ?: return@locked SnapshotSyncResult.UpToDate)
                is SnapshotAction.Download -> apply(ctx, action.snapshotId, emulatorId)
                is SnapshotAction.Conflict -> SnapshotSyncResult.Conflict(ctx.current, action.currentId)
            }
        }

    /**
     * Resolves a conflict for the local save: pushed on top of the channel's current, which
     * rewrites no history.
     */
    suspend fun keepLocal(
        gameId: Long,
        emulatorId: String,
        isHardcore: Boolean = false,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, emulatorId) ?: return@locked notReady(gameId, emulatorId)
        val local = sigilSaveHandler.collect(gameId, emulatorId) as? SigilCollect.Found
            ?: return@locked SnapshotSyncResult.Failed("no local save to keep")
        push(ctx, local, ctx.current?.id, isHardcore, approveHardcoreDowngrade)
    }

    /**
     * Pushes one cached unit from an offline chain. The first push of a chain the user chose to
     * keep goes on top of the channel's current ([onTopOfCurrent]); every later one expects the
     * snapshot the push before it made, so the chain lands in order or stops at the first conflict.
     */
    suspend fun pushCached(
        gameId: Long,
        emulatorId: String,
        unitFile: File,
        contentHash: String,
        identityHash: String?,
        onTopOfCurrent: Boolean,
        isHardcore: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, emulatorId) ?: return@locked notReady(gameId, emulatorId)
        val data = unitFile.readBytes()
        val unit = SigilCollect.Found(
            data = data,
            artifact = unitFile.name,
            contentHash = contentHash,
            identityHash = identityHash ?: contentHash,
            shape = SigilSaveHandler.unitShape(null, data)
        )
        val expected = if (onTopOfCurrent) ctx.current?.id else ctx.stored?.heldSnapshotId ?: ctx.current?.id
        push(ctx, unit, expected, isHardcore, false)
    }

    suspend fun keepServer(gameId: Long, emulatorId: String): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, emulatorId) ?: return@locked notReady(gameId, emulatorId)
        val current = ctx.current ?: return@locked SnapshotSyncResult.UpToDate
        apply(ctx, current.id, emulatorId)
    }

    private suspend fun <T> locked(gameId: Long, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        locks.computeIfAbsent(gameId) { Mutex() }.withLock { block() }
    }

    private suspend fun notReady(gameId: Long, emulatorId: String): SnapshotSyncResult =
        if (!isEligible(gameId, emulatorId)) SnapshotSyncResult.NotEligible else SnapshotSyncResult.NoConnection

    private suspend fun load(gameId: Long, emulatorId: String): ChannelView? {
        if (!isEligible(gameId, emulatorId)) return null
        val client = apiClient.get()
        val api = client.getApi() ?: return null
        val deviceId = client.getDeviceId() ?: return null
        val game = gameDao.getById(gameId) ?: return null
        val rommId = game.rommId ?: return null
        val file = launchedFile(api, game, rommId) ?: run {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | the launched file has no RomM file record")
            return null
        }
        val ownerUserId = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER
        val stored = channelDao.get(ownerUserId, gameId)?.takeIf { it.romFileId == file.id }
        val currents = runCatching { api.listCurrentSnapshots(listOf(file.id)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return null
        val current = currents.firstOrNull { it.channel?.id == stored?.channelId }
            ?: if (stored == null) currents.maxByOrNull { it.createdAt.orEmpty() } else null
        val legacyChannel = if (current == null && stored == null) legacyDefaultChannel(api, rommId) else null
        val channelId = current?.channel?.id ?: stored?.channelId ?: legacyChannel ?: UUID.randomUUID().toString()
        return ChannelView(
            api = api,
            deviceId = deviceId,
            ownerUserId = ownerUserId,
            game = game,
            file = file,
            stored = stored?.takeIf { it.channelId == channelId },
            current = current,
            channelId = channelId,
            isNewChannel = current == null && stored == null && legacyChannel == null,
            joinsLegacyChannel = legacyChannel != null
        )
    }

    private suspend fun legacyDefaultChannel(api: RomMApi, rommId: Long): String? =
        runCatching { api.getSavesByRom(rommId) }.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty()
            .filter { it.channelId != null && SaveSyncApiClient.syncKeyOf(it.slot) == SaveSyncApiClient.AUTOSAVE_SLOT_NAME }
            .maxByOrNull { it.updatedAt }
            ?.channelId

    private suspend fun launchedFile(api: RomMApi, game: GameEntity, rommId: Long): RomMRomFile? {
        val files = runCatching { api.getRom(rommId) }.getOrNull()?.body()?.files.orEmpty()
        if (files.isEmpty()) return null
        val launched = game.localPath?.let { File(it).name }
        files.firstOrNull { it.fileName == launched }?.let { return it }
        if (launched?.endsWith(".m3u", ignoreCase = true) == true) {
            return files.filter { it.discNumber == 1 }.minByOrNull { it.fileName } ?: files.minByOrNull { it.fileName }
        }
        return files.singleOrNull()
    }

    private fun held(ctx: ChannelView): SnapshotPoint? {
        val stored = ctx.stored ?: return null
        val id = stored.heldSnapshotId ?: return null
        val save = stored.heldSaveHash?.let { SaveHashes(it, stored.heldSaveIdentityHash ?: it) }
        return SnapshotPoint(id, save)
    }

    private fun currentPoint(current: RomMSnapshot?): SnapshotPoint? = current?.let { snapshot ->
        SnapshotPoint(snapshot.id, snapshot.save?.let { SaveHashes(it.contentHash, it.identityHash ?: it.contentHash) })
    }

    private suspend fun push(
        ctx: ChannelView,
        local: SigilCollect.Found,
        expectedCurrentId: Long?,
        isHardcore: Boolean,
        approveHardcoreDowngrade: Boolean
    ): SnapshotSyncResult {
        val manifest = JSONObject().apply {
            put("rom_file_id", ctx.file.id)
            put("channel_id", ctx.channelId)
            if (ctx.isNewChannel) put("label", DEFAULT_LABEL)
            put("expected_current_id", expectedCurrentId ?: JSONObject.NULL)
            put("save", JSONObject().apply {
                put("hash", local.contentHash)
                put("shape", local.shape)
                put("format", FORMAT_NEUTRAL)
            })
            put("is_hardcore", isHardcore)
            if (approveHardcoreDowngrade) put("approve_hardcore_downgrade", true)
            put("emulator", EMULATOR)
            put("emulator_version", BuildConfig.VERSION_NAME)
        }
        val parts = listOf(
            MultipartBody.Part.createFormData(
                "manifest", null, manifest.toString().toRequestBody(JSON)
            ),
            MultipartBody.Part.createFormData(
                "save", local.artifact, local.data.toRequestBody(OCTET_STREAM)
            )
        )
        val response = runCatching { ctx.api.pushSnapshot(ctx.deviceId, parts) }.getOrElse {
            return SnapshotSyncResult.Failed("push failed: ${it.message}")
        }
        val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        return when (response.code()) {
            200, 201 -> {
                val snapshot = body?.let { runCatching { snapshotAdapter.fromJson(it) }.getOrNull() }
                    ?: return SnapshotSyncResult.Failed("push answered ${response.code()} without a snapshot")
                record(ctx, snapshot.id, snapshot.digest, SaveHashes(local.contentHash, local.identityHash))
                Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | pushed #${snapshot.id} on ${ctx.channelId} expecting $expectedCurrentId")
                SnapshotSyncResult.Pushed(snapshot.id)
            }
            409 -> {
                val conflict = body?.let { runCatching { conflictAdapter.fromJson(it) }.getOrNull() }
                when {
                    conflict?.hardcoreDowngrade == true -> SnapshotSyncResult.HardcoreDowngrade(expectedCurrentId)
                    conflict?.current != null -> SnapshotSyncResult.Conflict(null, conflict.current.id)
                    else -> SnapshotSyncResult.Failed("push refused: 409 $body")
                }
            }
            422 -> if (ctx.joinsLegacyChannel && body?.contains(FILE_MISMATCH_FIELD) == true) {
                Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | legacy channel ${ctx.channelId} belongs to another file, starting a new one")
                push(ctx.asNewChannel(), local, null, isHardcore, approveHardcoreDowngrade)
            } else {
                SnapshotSyncResult.Failed("push refused: 422 $body")
            }
            else -> SnapshotSyncResult.Failed("push refused: ${response.code()} $body")
        }
    }

    private suspend fun adopt(ctx: ChannelView, current: RomMSnapshot): SnapshotSyncResult {
        record(ctx, current.id, current.digest, current.save?.let { SaveHashes(it.contentHash, it.identityHash ?: it.contentHash) })
        report(ctx, current.id)
        return SnapshotSyncResult.Applied(current.id)
    }

    private suspend fun apply(ctx: ChannelView, snapshotId: Long, emulator: String): SnapshotSyncResult {
        val snapshot = runCatching { ctx.api.getSnapshot(snapshotId) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
            ?: return SnapshotSyncResult.Failed("could not read snapshot #$snapshotId")
        val save = snapshot.save
        if (save != null) {
            val bytes = runCatching { ctx.api.downloadSaveContent(save.id) }.getOrNull()
                ?.takeIf { it.isSuccessful }?.body()?.bytes()
                ?: return SnapshotSyncResult.Failed("could not download save ${save.id}")
            val root = sigilSaveHandler.route(ctx.game.id, emulator)?.root
                ?: return SnapshotSyncResult.NotEligible
            if (!saveCacheManager.get().protectBeforeOverwrite(ctx.game.id, emulator, root)) {
                return SnapshotSyncResult.Failed("could not back up the local save before restoring")
            }
            val unit = File(context.cacheDir, "snapshot_${snapshot.id}_${System.nanoTime()}").apply { writeBytes(bytes) }
            try {
                when (val restored = sigilSaveHandler.restore(ctx.game.id, unit, emulator)) {
                    is SigilRestore.Refused -> return SnapshotSyncResult.Failed(restored.reason)
                    SigilRestore.NotRouted -> return SnapshotSyncResult.NotEligible
                    is SigilRestore.Restored -> Unit
                }
            } finally {
                unit.delete()
            }
            saveCacheManager.get().cacheCurrentSave(ctx.game.id, emulator, root)
        }
        record(ctx, snapshot.id, snapshot.digest, save?.let { SaveHashes(it.contentHash, it.identityHash ?: it.contentHash) })
        report(ctx, snapshot.id)
        Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | applied #${snapshot.id} from ${ctx.channelId}")
        return SnapshotSyncResult.Applied(snapshot.id)
    }

    private suspend fun record(ctx: ChannelView, snapshotId: Long, digest: String, save: SaveHashes?) {
        channelDao.upsert(
            SnapshotChannelEntity(
                ownerUserId = ctx.ownerUserId,
                gameId = ctx.game.id,
                channelId = ctx.channelId,
                romFileId = ctx.file.id,
                heldSnapshotId = snapshotId,
                heldDigest = digest,
                heldSaveHash = save?.contentHash,
                heldSaveIdentityHash = save?.identityHash,
                updatedAt = System.currentTimeMillis()
            )
        )
        saveCacheDao.clearAllDirtyFlags(ctx.game.id, syncPreferencesRepository.getRommUserId())
    }

    private suspend fun report(ctx: ChannelView, snapshotId: Long) {
        runCatching { ctx.api.reportSnapshotHeld(snapshotId, ctx.deviceId) }
            .onFailure { Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | report of #$snapshotId failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "SnapshotSyncEngine"
        private const val DEFAULT_LABEL = "Default"
        private const val FORMAT_NEUTRAL = "neutral"
        private const val EMULATOR = "argosy"
        private const val FILE_MISMATCH_FIELD = "rom_file_id"
        private val JSON = "application/json".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
