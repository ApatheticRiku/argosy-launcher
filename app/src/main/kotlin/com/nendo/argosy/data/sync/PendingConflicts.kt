package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.repository.SaveSyncResult
import java.time.Instant

fun SaveSyncResult.Conflict.toPendingConflict(
    fileName: String,
    slot: String?,
    emulatorId: String?,
    localUpdatedAt: Instant?,
    ownerUserId: Long
): PendingConflictEntity = PendingConflictEntity(
    gameId = gameId,
    rommSaveId = serverSaveId,
    fileName = fileName,
    slot = slot,
    emulator = emulatorId,
    localUpdatedAt = localUpdatedAt,
    serverUpdatedAt = serverTimestamp,
    localHash = localContentHash,
    serverHash = serverContentHash,
    reason = serverDeviceName?.let { "Server has newer save from $it" } ?: "Server has newer save",
    ownerUserId = ownerUserId
)

suspend fun PendingConflictDao.record(conflict: PendingConflictEntity): Long {
    val existing = findByGameSaveAndOwner(conflict.gameId, conflict.rommSaveId, conflict.slot, conflict.ownerUserId)
    return upsert(conflict.copy(id = existing?.id ?: 0L, dismissed = false))
}
