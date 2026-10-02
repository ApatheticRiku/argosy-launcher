package com.nendo.argosy.domain.usecase.save

import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import javax.inject.Inject

/**
 * Why [RestoreCachedSaveUseCase] could not put a cached or server save back into play.
 */
sealed class RestoreCachedSaveFailureReason {
    data object GameNotFound : RestoreCachedSaveFailureReason()
    data object NoLocalCopy : RestoreCachedSaveFailureReason()
    data object SaveLocationUnresolved : RestoreCachedSaveFailureReason()
    data object ClearExistingSaveFailed : RestoreCachedSaveFailureReason()
    data object NoLocalCacheId : RestoreCachedSaveFailureReason()
    data object NoServerSaveId : RestoreCachedSaveFailureReason()
    data object RestoreFailed : RestoreCachedSaveFailureReason()
}

class RestoreCachedSaveUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val emulatorResolver: EmulatorResolver
) {
    private val TAG = "RestoreCachedSaveUseCase"

    sealed class Result {
        data object Restored : Result()
        data object RestoredAndSynced : Result()
        data class Error(val reason: RestoreCachedSaveFailureReason) : Result()
    }

    suspend operator fun invoke(
        entry: UnifiedSaveEntry,
        gameId: Long,
        emulatorId: String,
        syncToServer: Boolean
    ): Result {
        val game = gameDao.getById(gameId)
            ?: return Result.Error(RestoreCachedSaveFailureReason.GameNotFound)
        if (game.localPath == null) {
            Log.d(TAG, "Skipping restore: game $gameId has no local ROM")
            return Result.Error(RestoreCachedSaveFailureReason.NoLocalCopy)
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val coreName = saveSyncRepository.resolveCoreForGame(gameId)

        val targetPath = saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        ) ?: saveSyncRepository.constructSavePath(
            emulatorId, game.title, game.platformSlug, game.localPath, coreName, game.saveId ?: game.titleId, gameId,
            folderShaped = entry.serverFileName?.endsWith(".zip", ignoreCase = true)
        ) ?: return Result.Error(RestoreCachedSaveFailureReason.SaveLocationUnresolved)

        val restoredCacheId = when (entry.source) {
            UnifiedSaveEntry.Source.LOCAL,
            UnifiedSaveEntry.Source.BOTH -> entry.localCacheId
                ?: return Result.Error(RestoreCachedSaveFailureReason.NoLocalCacheId)
            UnifiedSaveEntry.Source.SERVER -> {
                val serverSaveId = entry.serverSaveId
                    ?: return Result.Error(RestoreCachedSaveFailureReason.NoServerSaveId)
                saveSyncRepository.downloadToCache(serverSaveId, gameId, entry.channelName)
                    ?: return Result.Error(RestoreCachedSaveFailureReason.RestoreFailed)
            }
        }

        if (!saveCacheManager.protectBeforeOverwrite(gameId, emulatorId, targetPath)) {
            return Result.Error(RestoreCachedSaveFailureReason.ClearExistingSaveFailed)
        }

        val archiveRoots = saveCacheManager.archiveRootNames(restoredCacheId)
        if (!saveSyncRepository.clearSavesBeforeRestore(targetPath, game.platformSlug, game.saveId ?: game.titleId, archiveRoots)) {
            return Result.Error(RestoreCachedSaveFailureReason.ClearExistingSaveFailed)
        }

        if (!saveCacheManager.restoreSave(restoredCacheId, targetPath)) {
            return Result.Error(RestoreCachedSaveFailureReason.RestoreFailed)
        }

        val restoredContentHash = saveCacheManager.getCacheById(restoredCacheId)?.contentHash
            ?: saveCacheManager.calculateLocalSaveHash(targetPath, gameId, emulatorId)

        val targetChannel = entry.channelName
            ?: com.nendo.argosy.data.repository.SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        activeSaveRepository.activateCache(gameId, restoredCacheId)

        if (game.rommId != null) {
            saveSyncRepository.markRestored(
                gameId = gameId,
                rommId = game.rommId,
                emulatorId = emulatorId,
                channelName = targetChannel,
                localPath = targetPath,
                rommSaveId = entry.serverSaveId,
                serverTimestamp = entry.timestamp,
                contentHash = restoredContentHash
            )
        }

        if (entry.serverSaveId != null) {
            saveSyncRepository.confirmOrQueueDeviceSync(gameId, entry.serverSaveId)
        }

        if (syncToServer && game.rommId != null) {
            return when (val uploadResult = saveSyncRepository.uploadSave(gameId, emulatorId, targetChannel)) {
                is SaveSyncResult.Success -> Result.RestoredAndSynced
                is SaveSyncResult.Error -> {
                    Log.w(TAG, "Restored but failed to sync: ${uploadResult.message}")
                    Result.Restored
                }
                else -> Result.Restored
            }
        }

        return Result.Restored
    }

    suspend fun clearActiveSave(gameId: Long, emulatorId: String): Boolean {
        val game = gameDao.getById(gameId) ?: return true
        if (game.localPath == null) return true
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            gameId, game.platformId, game.platformSlug
        )
        val coreName = saveSyncRepository.resolveCoreForGame(gameId)
        val targetPath = saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        ) ?: return true
        return saveSyncRepository.clearSavesForTitle(targetPath, game.platformSlug, game.saveId ?: game.titleId)
    }
}
