package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConflictResolutionService"

sealed class ConflictResolutionOutcome {
    data object Dismissed : ConflictResolutionOutcome()
    data class Resolved(val syncResult: SaveSyncResult) : ConflictResolutionOutcome()
    data class Failed(val message: String) : ConflictResolutionOutcome()
}

@Singleton
class ConflictResolutionService @Inject constructor(
    private val pendingConflictDao: PendingConflictDao,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>
) {
    suspend fun resolve(
        conflict: PendingConflictEntity,
        resolution: ConflictResolution
    ): ConflictResolutionOutcome = withContext(Dispatchers.IO) {
        when (resolution) {
            ConflictResolution.SKIP -> {
                pendingConflictDao.dismiss(conflict.id)
                Logger.info(TAG, "[Resolve] SKIP gameId=${conflict.gameId} channel=${conflict.slot} conflictId=${conflict.id} -> dismissed")
                ConflictResolutionOutcome.Dismissed
            }
            ConflictResolution.KEEP_LOCAL -> {
                val emulatorId = resolveEmulator(conflict)
                    ?: return@withContext ConflictResolutionOutcome.Failed("Cannot resolve emulator for conflict ${conflict.id}")
                val result = if (conflict.isHardcoreDowngrade) {
                    saveSyncRepository.approveHardcoreDowngrade(conflict.gameId, emulatorId, conflict.slot)
                } else {
                    uploadLocal(conflict, emulatorId)
                }
                Logger.info(TAG, "[Resolve] KEEP_LOCAL gameId=${conflict.gameId} channel=${conflict.slot} emulator=$emulatorId -> $result")
                settle(conflict, result)
            }
            ConflictResolution.KEEP_SERVER -> {
                val emulatorId = resolveEmulator(conflict)
                    ?: return@withContext ConflictResolutionOutcome.Failed("Cannot resolve emulator for conflict ${conflict.id}")
                val result = saveSyncRepository.downloadSave(
                    gameId = conflict.gameId,
                    emulatorId = emulatorId,
                    channelName = conflict.slot,
                    knownServerSaveId = conflict.rommSaveId
                )
                Logger.info(TAG, "[Resolve] KEEP_SERVER gameId=${conflict.gameId} channel=${conflict.slot} emulator=$emulatorId -> $result")
                settle(conflict, result)
            }
        }
    }

    private suspend fun uploadLocal(conflict: PendingConflictEntity, emulatorId: String): SaveSyncResult {
        val slot = SaveSyncApiClient.namedChannelOrNull(conflict.slot)
            ?: return saveSyncRepository.uploadSave(
                gameId = conflict.gameId,
                emulatorId = emulatorId,
                channelName = conflict.slot,
                forceOverwrite = true
            )
        val owner = conflict.ownerUserId.takeIf { it != PendingConflictEntity.UNATTRIBUTED }
        val rommId = gameDao.getById(conflict.gameId)?.rommId
            ?: return SaveSyncResult.Error("Game ${conflict.gameId} is not on the server")
        val versions = saveCacheManager.get().getCachesForGameOnce(conflict.gameId)
            .filter { (it.ownerUserId == null || it.ownerUserId == owner) && !it.isRollback }
            .filter { SaveSyncApiClient.syncKeyOf(it.channelName) == SaveSyncApiClient.syncKeyOf(slot) }
        val chain = unsyncedChain(versions)
        if (chain.isEmpty()) return SaveSyncResult.Error("Slot $slot holds no cached version")
        conflict.rommSaveId?.let { serverSaveId ->
            if (!saveSyncRepository.downloadAndCacheSave(serverSaveId, conflict.gameId, slot, activate = false)) {
                Logger.warn(TAG, "[Resolve] could not keep server save $serverSaveId in history before keeping local")
            }
        }
        var result: SaveSyncResult = SaveSyncResult.Error("Slot $slot uploaded nothing")
        chain.forEachIndexed { index, cache ->
            result = saveSyncRepository.uploadCacheEntry(
                gameId = conflict.gameId,
                rommId = rommId,
                emulatorId = emulatorId,
                channelName = slot,
                cacheFile = saveCacheManager.get().getCacheFile(cache),
                contentHash = cache.contentHash,
                overwrite = index == 0,
                uploadedCacheId = cache.id
            )
            if (result !is SaveSyncResult.Success) return result
        }
        return result
    }

    private fun unsyncedChain(versions: List<SaveCacheEntity>): List<SaveCacheEntity> {
        val lastSynced = versions.filter { it.rommSaveId != null }.maxOfOrNull { it.cachedAt }
        val pending = versions
            .filter { it.rommSaveId == null && (lastSynced == null || it.cachedAt > lastSynced) }
            .sortedBy { it.cachedAt }
        return pending.ifEmpty { listOfNotNull(versions.maxByOrNull { it.cachedAt }) }
    }

    private suspend fun settle(conflict: PendingConflictEntity, result: SaveSyncResult): ConflictResolutionOutcome =
        when (result) {
            is SaveSyncResult.Success -> {
                pendingConflictDao.dismiss(conflict.id)
                ConflictResolutionOutcome.Resolved(result)
            }
            is SaveSyncResult.Error -> ConflictResolutionOutcome.Failed(result.message)
            else -> ConflictResolutionOutcome.Failed("Conflict ${conflict.id} left open: $result")
        }

    private suspend fun resolveEmulator(conflict: PendingConflictEntity): String? {
        conflict.emulator?.let { return it }
        val game = gameDao.getById(conflict.gameId) ?: return null
        return saveSyncRepository.resolveEmulatorForGame(game)
    }
}
