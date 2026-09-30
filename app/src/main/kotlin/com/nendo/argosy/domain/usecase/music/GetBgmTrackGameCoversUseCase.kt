package com.nendo.argosy.domain.usecase.music

import javax.inject.Inject

class GetBgmTrackGameCoversUseCase @Inject constructor(
    private val getBgmTrackGames: GetBgmTrackGamesUseCase
) {
    suspend operator fun invoke(gameFileIds: Collection<Long>): Map<Long, String> =
        getBgmTrackGames(gameFileIds)
            .mapNotNull { (fileId, game) -> game.coverPath?.let { fileId to it } }
            .toMap()
}
