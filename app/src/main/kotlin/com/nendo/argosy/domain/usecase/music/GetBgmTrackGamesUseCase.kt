package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.resolved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BgmTrackGame(
    val title: String,
    val coverPath: String?
)

class GetBgmTrackGamesUseCase @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao
) {
    suspend operator fun invoke(gameFileIds: Collection<Long>): Map<Long, BgmTrackGame> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (fileId in gameFileIds.toSet()) {
                    val gameId = gameFileDao.getById(fileId)?.gameId ?: continue
                    val game = gameDao.getById(gameId) ?: continue
                    val cover = gameArtDao.resolved(gameId).coverPath?.takeIf { it.isNotBlank() }
                    put(fileId, BgmTrackGame(game.title, cover))
                }
            }
        }
}
