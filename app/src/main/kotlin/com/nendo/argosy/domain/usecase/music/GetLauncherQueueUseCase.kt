package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetLauncherQueueUseCase @Inject constructor(
    private val bgmPlaylistRepository: BgmPlaylistRepository,
    private val getBgmTrackGames: GetBgmTrackGamesUseCase
) {
    suspend operator fun invoke(): List<MusicQueueTrack> = withContext(Dispatchers.IO) {
        val rows = bgmPlaylistRepository.playableTracks()
        val games = getBgmTrackGames(rows.mapNotNull { it.gameFileId })
        rows.map { row ->
            val game = row.gameFileId?.let { games[it] }
            MusicQueueTrack(
                id = localTrackId(row.filePath),
                source = MusicTrackSource.Local(row.filePath),
                title = row.displayName,
                gameTitle = game?.title,
                coverPath = game?.coverPath
            )
        }
    }
}

fun localTrackId(path: String): String = "path:$path"

fun serverTrackId(romFileId: Long): String = "romm:$romFileId"
