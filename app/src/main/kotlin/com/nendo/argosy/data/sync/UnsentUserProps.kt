package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.SyncType

/**
 * The per-user properties of one game that hold a local edit RomM has not received yet. A server
 * `rom_user` value for a property in this set must not replace the local one.
 */
class UnsentUserProps(private val types: Set<SyncType>) {
    fun keepsLocal(type: SyncType): Boolean = type in types
}

class UnsentUserPropsByGame(private val byGame: Map<Long, Set<SyncType>>) {
    fun forGame(gameId: Long): UnsentUserProps = UnsentUserProps(byGame[gameId].orEmpty())
}

suspend fun PendingSyncQueueDao.unsentUserProps(gameId: Long, ownerUserId: Long?): UnsentUserProps =
    loadUnsentUserProps(ownerUserId, gameId).forGame(gameId)

suspend fun PendingSyncQueueDao.unsentUserPropsByGame(ownerUserId: Long?): UnsentUserPropsByGame =
    loadUnsentUserProps(ownerUserId, gameId = null)

private suspend fun PendingSyncQueueDao.loadUnsentUserProps(ownerUserId: Long?, gameId: Long?): UnsentUserPropsByGame =
    UnsentUserPropsByGame(
        getUnsentSyncTypesForOwnerOrUnowned(ownerUserId, gameId)
            .groupBy({ it.gameId }, { it.syncType })
            .mapValues { (_, types) -> types.toSet() }
    )
