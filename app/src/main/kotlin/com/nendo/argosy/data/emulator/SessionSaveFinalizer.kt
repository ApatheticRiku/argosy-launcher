package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.SaveAccessNotices
import com.nendo.argosy.data.sync.SaveLookup
import com.nendo.argosy.data.sync.record
import com.nendo.argosy.data.sync.toPendingConflict
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.util.Logger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SessionSaveInput(
    val gameId: Long,
    val emulatorPackage: String,
    val coreName: String?,
    val isHardcore: Boolean,
    val channelName: String?,
    val startTime: Instant,
    val variantFileId: Long?,
    val isNetplayGuest: Boolean
)

sealed interface SessionSaveOutcome {
    data object Exempt : SessionSaveOutcome
    data object NothingToSave : SessionSaveOutcome
    data class Unreadable(val dirPath: String, val emulatorId: String) : SessionSaveOutcome
    data object CacheFailed : SessionSaveOutcome
    data class Synced(
        val cache: SaveCacheManager.CacheResult,
        val sync: SyncSaveOnSessionEndUseCase.Result,
        val conflictId: Long?
    ) : SessionSaveOutcome

    val isSettled: Boolean get() = this !is CacheFailed
}

@Singleton
class SessionSaveFinalizer @Inject constructor(
    private val gameDao: GameDao,
    private val saveCacheDao: SaveCacheDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val pendingConflictDao: PendingConflictDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val emulatorResolver: EmulatorResolver,
    private val saveAccessNotices: SaveAccessNotices,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val syncSaveOnSessionEnd: dagger.Lazy<SyncSaveOnSessionEndUseCase>,
    private val snapshotRouter: dagger.Lazy<com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter>
) {
    suspend fun finalize(input: SessionSaveInput): SessionSaveOutcome {
        if (input.variantFileId != null || input.isNetplayGuest) return SessionSaveOutcome.Exempt
        val game = gameDao.getById(input.gameId) ?: return SessionSaveOutcome.NothingToSave
        val emulatorId = emulatorResolver.resolveSessionEmulator(
            game.id, game.platformId, game.platformSlug, input.emulatorPackage
        )?.emulatorId ?: run {
            Logger.warn(TAG, "[SaveSync] SESSION gameId=${input.gameId} | Cannot resolve emulator | package=${input.emulatorPackage}")
            return SessionSaveOutcome.NothingToSave
        }

        val lookup = saveSyncRepository.get().discoverSavePathChecked(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = input.coreName,
            emulatorPackage = input.emulatorPackage,
            gameId = input.gameId
        )
        val savePath = when (lookup) {
            is SaveLookup.Unreadable -> {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=${input.gameId} | Save folder exists but cannot be read | dir=${lookup.dirPath}, emulator=$emulatorId")
                saveAccessNotices.record(lookup.dirPath, emulatorId)
                return SessionSaveOutcome.Unreadable(lookup.dirPath, emulatorId)
            }
            SaveLookup.Absent -> return SessionSaveOutcome.NothingToSave
            is SaveLookup.Found -> lookup.path
        }

        val activeChannel = snapshotRouter.get().sessionChannel(input.gameId, input.isHardcore, input.channelName)
        val cache = saveCacheManager.get().cacheCurrentSave(
            gameId = input.gameId,
            emulatorId = emulatorId,
            savePath = savePath,
            channelName = activeChannel,
            isLocked = false,
            isHardcore = input.isHardcore,
            skipDuplicateCheck = false,
            coreName = input.coreName,
            claimNewSaves = true
        )
        val cacheId = when (cache) {
            is SaveCacheManager.CacheResult.Created -> cache.cacheId
            is SaveCacheManager.CacheResult.Duplicate -> cache.cacheId
            SaveCacheManager.CacheResult.Failed -> {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=${input.gameId} | Failed to cache save, keeping the session for recovery | path=$savePath")
                return SessionSaveOutcome.CacheFailed
            }
        }
        activeSaveRepository.activateCache(input.gameId, cacheId)
        activeSaveRepository.setActiveSaveApplied(input.gameId, false)

        val sync = syncSaveOnSessionEnd.get()(
            gameId = input.gameId,
            emulatorPackage = input.emulatorPackage,
            sessionStartTime = input.startTime.toEpochMilli(),
            coreName = input.coreName,
            isHardcore = input.isHardcore,
            channelName = input.channelName
        )
        val ownerUserId = activeSaveRepository.activeOwnerId()
        var conflictId: Long? = null
        when (sync) {
            is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                linkCacheToServer(input.gameId, activeChannel, sync, cacheId, ownerUserId)
                pendingSyncQueueDao.deleteActiveByGameAndType(input.gameId, SyncType.SAVE_FILE, ownerUserId)
            }
            is SyncSaveOnSessionEndUseCase.Result.Conflict -> {
                conflictId = pendingConflictDao.record(
                    sync.upload.toPendingConflict(
                        fileName = sync.channelName ?: game.title,
                        slot = sync.channelName,
                        emulatorId = sync.emulatorId,
                        localUpdatedAt = sync.upload.localTimestamp,
                        ownerUserId = ownerUserId ?: PendingConflictEntity.UNATTRIBUTED
                    )
                )
                saveCacheDao.clearAllDirtyFlags(input.gameId, ownerUserId)
            }
            SyncSaveOnSessionEndUseCase.Result.NoChange,
            SyncSaveOnSessionEndUseCase.Result.Queued -> saveCacheDao.clearAllDirtyFlags(input.gameId, ownerUserId)
            SyncSaveOnSessionEndUseCase.Result.NoSaveFound,
            SyncSaveOnSessionEndUseCase.Result.NotConfigured,
            is SyncSaveOnSessionEndUseCase.Result.Error -> Unit
        }
        return SessionSaveOutcome.Synced(cache, sync, conflictId)
    }

    private suspend fun linkCacheToServer(
        gameId: Long,
        channelName: String?,
        upload: SyncSaveOnSessionEndUseCase.Result.Uploaded,
        cacheId: Long,
        ownerUserId: Long?
    ) {
        saveCacheDao.markSynced(cacheId, Instant.now())
        upload.rommSaveId?.let { rommSaveId ->
            saveCacheDao.updateRommSaveId(cacheId, rommSaveId)
            upload.serverTimestamp?.let { saveCacheDao.updateCachedAt(cacheId, it) }
            com.nendo.argosy.util.SaveDebugLogger.logLinkCache(
                gameId = gameId,
                channel = channelName,
                cacheId = cacheId,
                rommSaveId = rommSaveId,
                serverTimestamp = upload.serverTimestamp,
                method = "byUploadedCacheId"
            )
        }
        if (channelName != null) {
            saveCacheDao.clearDirtyFlagForChannel(gameId, ownerUserId, channelName, excludeId = -1)
        } else {
            saveCacheDao.clearAllDirtyFlags(gameId, ownerUserId)
        }
    }

    private companion object {
        const val TAG = "SessionSaveFinalizer"
    }
}
