package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.domain.model.SaveListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveListStatusRepository @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWorstByGame(): Flow<Map<Long, SaveListState>> =
        syncPreferencesRepository.preferences
            .map { it.rommUserId }
            .distinctUntilChanged()
            .flatMapLatest { ownerUserId ->
                saveSyncDao.observeGameSyncStatuses(
                    ownerUserId = ownerUserId,
                    conflictOwners = PendingConflictEntity.ownerScope(ownerUserId)
                )
            }
            .map { rows -> SaveListState.worstByGame(rows.map { it.gameId to it.syncStatus }) }
            .distinctUntilChanged()
}
