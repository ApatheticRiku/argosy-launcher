package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import javax.inject.Inject

class SyncVirtualCollectionsUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val collectionDao: CollectionDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend operator fun invoke() {
        val games = gameDao.getSyncEnabledGamesForCategories(
            syncPreferencesRepository.getRommUserId()
        )

        val genreMap = mutableMapOf<String, MutableSet<Long>>()
        val modeMap = mutableMapOf<String, MutableSet<Long>>()

        games.forEach { game ->
            game.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { genre ->
                genreMap.getOrPut(genre) { mutableSetOf() }.add(game.id)
            }
            game.gameModes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { mode ->
                modeMap.getOrPut(mode) { mutableSetOf() }.add(game.id)
            }
        }

        collectionDao.replaceCollectionsOfType(CollectionType.GENRE, genreMap)
        collectionDao.replaceCollectionsOfType(CollectionType.GAME_MODE, modeMap)
    }
}
