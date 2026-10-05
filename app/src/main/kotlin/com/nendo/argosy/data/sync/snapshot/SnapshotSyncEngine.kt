package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.emulator.EmulatorResolver
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
import com.nendo.argosy.data.repository.SaveDownloader
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.SigilCollect
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
 * A save as it travels in a push: [format] is `neutral` for a unit Sigil built and `native` for the
 * files the emulator wrote, packed the way the legacy upload packs them.
 */
data class SnapshotUnit(
    val data: ByteArray,
    val name: String,
    val contentHash: String,
    val identityHash: String,
    val shape: String,
    val format: String
)

/**
 * Save sync against RomM's snapshot API for every game RomM knows, on every Argosy save channel:
 * list the channel's current, decide on the device, then push or download. States are never
 * listed in a push, so every snapshot carries its parent's bank.
 */
@Singleton
class SnapshotSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val channelDao: SnapshotChannelDao,
    private val saveCacheDao: SaveCacheDao,
    private val sigilSaveHandler: SigilSaveHandler,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val saveDownloader: Lazy<SaveDownloader>,
    private val apiClient: Lazy<SaveSyncApiClient>,
    private val emulatorResolver: EmulatorResolver,
    private val saveArchiver: SaveArchiver,
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
        val label: String,
        val stored: SnapshotChannelEntity?,
        val current: RomMSnapshot?,
        val channelId: String,
        val isNewChannel: Boolean,
        val joinsLegacyChannel: Boolean = false
    ) {
        fun asNewChannel(): ChannelView = ChannelView(
            api, deviceId, ownerUserId, game, file, label, null, null, UUID.randomUUID().toString(), isNewChannel = true
        )
    }

    private sealed class LocalSave {
        data object None : LocalSave()
        data class Unreadable(val reason: String) : LocalSave()
        data class Sigil(val unit: SigilCollect.Found) : LocalSave()
        data class Native(val savePath: String, val contentHash: String) : LocalSave()

        val hashes: SaveHashes?
            get() = when (this) {
                is Sigil -> SaveHashes(unit.contentHash, unit.identityHash)
                is Native -> SaveHashes(contentHash, contentHash)
                else -> null
            }
    }

    suspend fun isEligible(gameId: Long): Boolean =
        apiClient.get().getCapabilities().supportsSnapshots && gameDao.getById(gameId)?.rommId != null

    suspend fun sync(gameId: Long, emulatorId: String, channelName: String?, isHardcore: Boolean = false): SnapshotSyncResult =
        locked(gameId) {
            val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
            val local = localSave(ctx.game, emulatorId)
            if (local is LocalSave.Unreadable) return@locked SnapshotSyncResult.Failed(local.reason)
            val action = SnapshotDecision.decide(held(ctx), currentPoint(ctx.current), local.hashes)
            Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=$gameId channel=${ctx.label}/${ctx.channelId} held=${ctx.stored?.heldSnapshotId} current=${ctx.current?.id} | $action")
            when (action) {
                SnapshotAction.Nothing -> SnapshotSyncResult.UpToDate
                is SnapshotAction.Push -> pushLocal(ctx, local, emulatorId, channelName, action.expectedCurrentId, isHardcore, false)
                is SnapshotAction.Adopt -> adopt(ctx, ctx.current ?: return@locked SnapshotSyncResult.UpToDate)
                is SnapshotAction.Download -> apply(ctx, action.snapshotId, emulatorId, channelName)
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
        channelName: String?,
        isHardcore: Boolean = false,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val local = localSave(ctx.game, emulatorId)
        pushLocal(ctx, local, emulatorId, channelName, ctx.current?.id, isHardcore, approveHardcoreDowngrade)
    }

    /**
     * Pushes one cached save from an offline chain. The first push of a chain the user chose to
     * keep goes on top of the channel's current ([onTopOfCurrent]); every later one expects the
     * snapshot the push before it made, so the chain lands in order or stops at the first conflict.
     */
    suspend fun pushCached(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        cacheFile: File,
        onTopOfCurrent: Boolean,
        isHardcore: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val format = if (sigilSaveHandler.route(gameId, emulatorId) != null) FORMAT_NEUTRAL else FORMAT_NATIVE
        val unit = unitFromCache(cacheFile, format) ?: return@locked SnapshotSyncResult.Failed("cached save ${cacheFile.name} is empty")
        val expected = if (onTopOfCurrent) ctx.current?.id else ctx.stored?.heldSnapshotId ?: ctx.current?.id
        push(ctx, unit, expected, isHardcore, false)
    }

    suspend fun keepServer(gameId: Long, emulatorId: String, channelName: String?): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val current = ctx.current ?: return@locked SnapshotSyncResult.UpToDate
        apply(ctx, current.id, emulatorId, channelName)
    }

    private suspend fun <T> locked(gameId: Long, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        locks.computeIfAbsent(gameId) { Mutex() }.withLock { block() }
    }

    private suspend fun notReady(gameId: Long): SnapshotSyncResult =
        if (!isEligible(gameId)) SnapshotSyncResult.NotEligible else SnapshotSyncResult.NoConnection

    private fun labelOf(channelName: String?): String =
        SaveSyncApiClient.namedChannelOrNull(channelName) ?: DEFAULT_LABEL

    private suspend fun load(gameId: Long, channelName: String?): ChannelView? {
        if (!isEligible(gameId)) return null
        val client = apiClient.get()
        val api = client.getApi() ?: return null
        val deviceId = client.getDeviceId() ?: return null
        val game = gameDao.getById(gameId) ?: return null
        val rommId = game.rommId ?: return null
        val file = launchedFile(api, game, rommId) ?: run {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | the launched file has no RomM file record")
            return null
        }
        val label = labelOf(channelName)
        val ownerUserId = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER
        val stored = channelDao.get(ownerUserId, gameId, label)?.takeIf { it.romFileId == file.id }
        val currents = runCatching { api.listCurrentSnapshots(listOf(file.id)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return null
        val current = currents.firstOrNull { it.channel?.id == stored?.channelId }
            ?: if (stored == null) {
                currents.filter { it.channel?.label.equals(label, ignoreCase = true) }.maxByOrNull { it.createdAt.orEmpty() }
            } else null
        val legacyChannel = if (current == null && stored == null) legacyChannel(api, rommId, channelName) else null
        val channelId = current?.channel?.id ?: stored?.channelId ?: legacyChannel ?: UUID.randomUUID().toString()
        return ChannelView(
            api = api,
            deviceId = deviceId,
            ownerUserId = ownerUserId,
            game = game,
            file = file,
            label = label,
            stored = stored?.takeIf { it.channelId == channelId },
            current = current,
            channelId = channelId,
            isNewChannel = current == null && stored == null && legacyChannel == null,
            joinsLegacyChannel = legacyChannel != null
        )
    }

    private suspend fun legacyChannel(api: RomMApi, rommId: Long, channelName: String?): String? {
        val key = SaveSyncApiClient.syncKeyOf(channelName)
        return runCatching { api.getSavesByRom(rommId) }.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty()
            .filter { it.channelId != null && it.slot != null && SaveSyncApiClient.equalsNormalized(SaveSyncApiClient.syncKeyOf(it.slot), key) }
            .maxByOrNull { it.updatedAt }
            ?.channelId
    }

    private suspend fun launchedFile(api: RomMApi, game: GameEntity, rommId: Long): RomMRomFile? {
        val files = runCatching { api.getRom(rommId) }.getOrNull()?.body()?.files.orEmpty()
        if (files.isEmpty()) return null
        val launched = game.localPath?.let { File(it).name }
        files.firstOrNull { it.fileName == launched }?.let { return it }
        val games = files
            .filter { it.category == null || it.category.equals(GAME_FILE_CATEGORY, ignoreCase = true) }
            .sortedBy { it.fileName.lowercase() }
        return games.firstOrNull { it.fileName.substringAfterLast('.').lowercase() in LOADER_EXTENSIONS }
            ?: games.firstOrNull()
    }

    private suspend fun localSave(game: GameEntity, emulatorId: String): LocalSave =
        when (val collected = sigilSaveHandler.collect(game.id, emulatorId)) {
            is SigilCollect.Found -> LocalSave.Sigil(collected)
            SigilCollect.Absent -> LocalSave.None
            is SigilCollect.Unreadable -> LocalSave.Unreadable(collected.reason)
            SigilCollect.NotRouted -> nativeSave(game, emulatorId)
        }

    private suspend fun nativeSave(game: GameEntity, emulatorId: String): LocalSave {
        val client = apiClient.get()
        val savePath = client.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = client.resolveCoreForGame(game, emulatorId),
            emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug),
            gameId = game.id
        ) ?: return LocalSave.None
        val hash = saveCacheManager.get().calculateLocalSaveHash(savePath, game.id, emulatorId) ?: return LocalSave.None
        return LocalSave.Native(savePath, hash)
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

    private suspend fun pushLocal(
        ctx: ChannelView,
        local: LocalSave,
        emulatorId: String,
        channelName: String?,
        expectedCurrentId: Long?,
        isHardcore: Boolean,
        approveHardcoreDowngrade: Boolean
    ): SnapshotSyncResult {
        val unit = when (local) {
            is LocalSave.Sigil -> SnapshotUnit(
                local.unit.data, local.unit.artifact, local.unit.contentHash, local.unit.identityHash,
                local.unit.shape, FORMAT_NEUTRAL
            )
            is LocalSave.Native -> nativeUnit(ctx.game.id, emulatorId, channelName, local.savePath, isHardcore)
                ?: return SnapshotSyncResult.Failed("could not pack the save at ${local.savePath}")
            is LocalSave.Unreadable -> return SnapshotSyncResult.Failed(local.reason)
            LocalSave.None -> return SnapshotSyncResult.Failed("no local save to push")
        }
        return push(ctx, unit, expectedCurrentId, isHardcore, approveHardcoreDowngrade)
    }

    private suspend fun nativeUnit(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        savePath: String,
        isHardcore: Boolean
    ): SnapshotUnit? {
        val cache = saveCacheManager.get()
        val cacheId = when (val cached = cache.cacheCurrentSave(gameId, emulatorId, savePath, channelName, isHardcore = isHardcore)) {
            is SaveCacheManager.CacheResult.Created -> cached.cacheId
            is SaveCacheManager.CacheResult.Duplicate -> cached.cacheId
            SaveCacheManager.CacheResult.Failed -> return null
        }
        val entity = cache.getCacheById(cacheId) ?: return null
        return unitFromCache(cache.getCacheFile(entity), FORMAT_NATIVE)
    }

    private fun unitFromCache(file: File, format: String): SnapshotUnit? {
        val data = saveArchiver.readBytesWithoutTrailer(file)?.takeIf { saveArchiver.hasHardcoreTrailer(file) }
            ?: file.takeIf { it.isFile }?.readBytes()
            ?: return null
        if (data.isEmpty()) return null
        val hashFile = File(context.cacheDir, "snapshot_hash_${System.nanoTime()}").apply { writeBytes(data) }
        val hash = try {
            saveArchiver.calculateContentHash(hashFile)
        } finally {
            hashFile.delete()
        }
        return SnapshotUnit(data, file.name, hash, hash, SigilSaveHandler.unitShape(null, data), format)
    }

    private suspend fun push(
        ctx: ChannelView,
        unit: SnapshotUnit,
        expectedCurrentId: Long?,
        isHardcore: Boolean,
        approveHardcoreDowngrade: Boolean
    ): SnapshotSyncResult {
        val manifest = JSONObject().apply {
            put("rom_file_id", ctx.file.id)
            put("channel_id", ctx.channelId)
            if (ctx.isNewChannel) put("label", ctx.label)
            put("expected_current_id", expectedCurrentId ?: JSONObject.NULL)
            put("save", JSONObject().apply {
                put("hash", unit.contentHash)
                put("shape", unit.shape)
                put("format", unit.format)
            })
            put("is_hardcore", isHardcore)
            if (approveHardcoreDowngrade) put("approve_hardcore_downgrade", true)
            put("emulator", EMULATOR)
            put("emulator_version", BuildConfig.VERSION_NAME)
        }
        val parts = listOf(
            MultipartBody.Part.createFormData("manifest", null, manifest.toString().toRequestBody(JSON)),
            MultipartBody.Part.createFormData("save", unit.name, unit.data.toRequestBody(OCTET_STREAM))
        )
        val response = runCatching { ctx.api.pushSnapshot(ctx.deviceId, parts) }.getOrElse {
            return SnapshotSyncResult.Failed("push failed: ${it.message}")
        }
        val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        return when (response.code()) {
            200, 201 -> {
                val snapshot = body?.let { runCatching { snapshotAdapter.fromJson(it) }.getOrNull() }
                    ?: return SnapshotSyncResult.Failed("push answered ${response.code()} without a snapshot")
                record(ctx, snapshot.id, snapshot.digest, SaveHashes(unit.contentHash, unit.identityHash))
                Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | pushed #${snapshot.id} (${unit.format}) on ${ctx.label}/${ctx.channelId} expecting $expectedCurrentId")
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
                push(ctx.asNewChannel(), unit, null, isHardcore, approveHardcoreDowngrade)
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

    private suspend fun apply(ctx: ChannelView, snapshotId: Long, emulatorId: String, channelName: String?): SnapshotSyncResult {
        val snapshot = runCatching { ctx.api.getSnapshot(snapshotId) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
            ?: return SnapshotSyncResult.Failed("could not read snapshot #$snapshotId")
        val save = snapshot.save
        if (save != null) {
            val placed = saveDownloader.get().downloadSave(
                gameId = ctx.game.id,
                emulatorId = emulatorId,
                channelName = channelName,
                knownServerSaveId = save.id,
                fromSnapshot = true
            )
            when (placed) {
                is SaveSyncResult.Success -> Unit
                is SaveSyncResult.Error -> return SnapshotSyncResult.Failed(placed.message)
                else -> return SnapshotSyncResult.Failed("save ${save.id} was not placed: ${placed::class.simpleName}")
            }
        }
        record(ctx, snapshot.id, snapshot.digest, save?.let { SaveHashes(it.contentHash, it.identityHash ?: it.contentHash) })
        report(ctx, snapshot.id)
        Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | applied #${snapshot.id} from ${ctx.label}/${ctx.channelId}")
        return SnapshotSyncResult.Applied(snapshot.id)
    }

    private suspend fun record(ctx: ChannelView, snapshotId: Long, digest: String, save: SaveHashes?) {
        channelDao.upsert(
            SnapshotChannelEntity(
                ownerUserId = ctx.ownerUserId,
                gameId = ctx.game.id,
                label = ctx.label,
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
        private const val DEFAULT_LABEL = "default"
        private const val FORMAT_NEUTRAL = "neutral"
        private const val FORMAT_NATIVE = "native"
        private const val EMULATOR = "argosy"
        private const val FILE_MISMATCH_FIELD = "rom_file_id"
        private const val GAME_FILE_CATEGORY = "game"
        private val LOADER_EXTENSIONS = setOf("cue", "gdi", "ccd", "mds", "toc")
        private val JSON = "application/json".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
