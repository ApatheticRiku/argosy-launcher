package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.resolved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetLocalGameCoversUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao
) {
    suspend operator fun invoke(rommIds: Collection<Long>): Map<Long, String> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (rommId in rommIds) {
                    val game = gameDao.getByRommId(rommId) ?: continue
                    val coverPath = gameArtDao.resolved(game.id).coverPath
                    if (!coverPath.isNullOrBlank()) put(rommId, coverPath)
                }
            }
        }
}
