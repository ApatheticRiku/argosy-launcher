package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject

class ResolveMusicQueueUseCase @Inject constructor(
    private val getLauncherQueue: GetLauncherQueueUseCase,
    private val musicLibrary: RomMMusicLibraryService,
    private val getLocalMusicTrackState: GetLocalMusicTrackStateUseCase,
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(selection: MusicSelection): List<MusicQueueTrack> =
        withContext(Dispatchers.IO) {
            when (selection) {
                MusicSelection.Launcher -> getLauncherQueue()
                is MusicSelection.ServerPlaylist -> serverPlaylistQueue(selection)
                is MusicSelection.GameSoundtrack -> soundtrackQueue(selection)
            }
        }

    private suspend fun serverPlaylistQueue(selection: MusicSelection.ServerPlaylist): List<MusicQueueTrack> {
        if (!musicLibrary.isConnected()) return emptyList()
        val token = preferencesRepository.userPreferences.first().rommToken
        val result = musicLibrary.getPlaylistTracks(selection.playlistId)
        if (result !is RomMResult.Success) return emptyList()
        return result.data.toQueue(token, fallbackGame = null)
    }

    private suspend fun soundtrackQueue(selection: MusicSelection.GameSoundtrack): List<MusicQueueTrack> {
        val romId = selection.romId
        val game = selection.gameId?.let { gameDao.getById(it) } ?: romId?.let { gameDao.getByRommId(it) }
        val canStream = musicLibrary.isConnected() &&
            musicLibrary.capabilities().supportsMusicTrackRomFilter
        if (romId != null && canStream) {
            val token = preferencesRepository.userPreferences.first().rommToken
            val result = musicLibrary.getGameTracks(romId, BGM_MIN_DURATION_SECONDS)
            if (result is RomMResult.Success && result.data.isNotEmpty()) {
                val ordered = result.data.sortedWith(
                    compareBy(
                        { it.disc ?: Int.MAX_VALUE },
                        { it.track ?: Int.MAX_VALUE },
                        { it.title.orEmpty().lowercase() }
                    )
                )
                return ordered.toQueue(token, game)
            }
        }
        return game?.let { localSoundtrack(it) }.orEmpty()
    }

    private suspend fun localSoundtrack(game: GameEntity): List<MusicQueueTrack> =
        gameFileDao.getFilesByCategory(game.id, VariantCategory.SOUNDTRACK.key)
            .filter { isPlayableLocal(it.localPath, it.durationSeconds) }
            .sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.fileName }))
            .mapNotNull { row ->
                val path = row.localPath ?: return@mapNotNull null
                MusicQueueTrack(
                    id = localTrackId(path),
                    source = MusicTrackSource.Local(path),
                    title = row.trackTitle?.takeIf { it.isNotBlank() } ?: row.fileName.substringBeforeLast('.'),
                    gameTitle = game.title,
                    coverPath = game.coverPath?.takeIf { it.isNotBlank() }
                )
            }

    private suspend fun List<RomMMusicTrack>.toQueue(
        token: String?,
        fallbackGame: GameEntity?
    ): List<MusicQueueTrack> {
        val locals = getLocalMusicTrackState(map { it.toLookup() })
        val localCovers = mutableMapOf<Long, String?>()
        return mapNotNull { track ->
            val source = locals[track.romFileId]?.let { MusicTrackSource.Local(it.localPath) }
                ?: remoteSource(track.streamUrl, token)
                ?: return@mapNotNull null
            val localCover = localCovers.getOrPut(track.romId) {
                gameDao.getByRommId(track.romId)?.coverPath?.takeIf { it.isNotBlank() }
            }
            MusicQueueTrack(
                id = serverTrackId(track.romFileId),
                source = source,
                title = track.title?.takeIf { it.isNotBlank() } ?: track.fileName(),
                gameTitle = track.gameName?.takeIf { it.isNotBlank() } ?: fallbackGame?.title,
                coverPath = localCover ?: musicLibrary.resourceUrl(track.coverUrl ?: track.gameCoverUrl)
            )
        }
    }

    private fun remoteSource(streamPath: String, token: String?): MusicTrackSource.Remote? {
        val url = musicLibrary.streamUrl(streamPath) ?: return null
        val headers = if (token != null && musicLibrary.isServerOrigin(url)) {
            mapOf("Authorization" to "Bearer $token")
        } else {
            emptyMap()
        }
        return MusicTrackSource.Remote(url, headers)
    }

    private fun RomMMusicTrack.fileName(): String {
        val encoded = streamUrl.substringAfterLast('/')
        return runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(encoded)
    }

    private fun RomMMusicTrack.toLookup() = MusicTrackLookup(
        romFileId = romFileId,
        platformName = platformName,
        gameName = gameName.orEmpty(),
        trackNumber = track,
        trackTitle = title?.takeIf { it.isNotBlank() },
        fileName = fileName()
    )
}
