package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.remote.romm.RomMMusicGame
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicServerStatus
import com.nendo.argosy.domain.model.SoundtrackGameEntry
import com.nendo.argosy.domain.model.SoundtrackGamePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class SearchSoundtrackGamesUseCase @Inject constructor(
    private val musicLibrary: RomMMusicLibraryService,
    private val gameFileDao: GameFileDao,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao
) {
    suspend operator fun invoke(query: String?, offset: Int, limit: Int): SoundtrackGamePage =
        withContext(Dispatchers.IO) {
            val search = query?.trim()?.takeIf { it.isNotEmpty() }
            val status = when {
                !musicLibrary.isConnected() -> MusicServerStatus.OFFLINE
                !musicLibrary.capabilities().supportsMusicGames -> MusicServerStatus.UNSUPPORTED
                else -> null
            }
            if (status != null) return@withContext localPage(search, offset, status)
            when (val result = musicLibrary.getGames(search, BGM_MIN_DURATION_SECONDS, limit, offset)) {
                is RomMResult.Success -> SoundtrackGamePage(
                    entries = result.data.items.map { it.toEntry() },
                    hasMore = offset + result.data.items.size < result.data.total,
                    fromServer = true,
                    serverStatus = MusicServerStatus.AVAILABLE
                )
                is RomMResult.Error -> localPage(search, offset, musicServerStatusFor(result.code))
            }
        }

    private suspend fun RomMMusicGame.toEntry(): SoundtrackGameEntry {
        val localGame = gameDao.getByRommId(romId)
        val localCover = localGame?.coverPath?.takeIf { it.isNotBlank() }
        return SoundtrackGameEntry(
            selection = MusicSelection.GameSoundtrack(romId = romId, gameId = localGame?.id, title = name),
            platformName = platformName,
            coverPath = localCover ?: musicLibrary.resourceUrl(coverUrl),
            trackCount = count
        )
    }

    private suspend fun localPage(
        search: String?,
        offset: Int,
        status: MusicServerStatus
    ): SoundtrackGamePage {
        if (offset > 0) {
            return SoundtrackGamePage(emptyList(), hasMore = false, fromServer = false, serverStatus = status)
        }
        val countsByGame = gameFileDao.getLocalFilesByCategory(VariantCategory.SOUNDTRACK.key)
            .filter { isPlayableLocal(it.localPath, it.durationSeconds) }
            .groupingBy { it.gameId }
            .eachCount()
        val entries = if (countsByGame.isEmpty()) {
            emptyList()
        } else {
            gameDao.getByIds(countsByGame.keys.toList())
                .filter { search == null || it.title.contains(search, ignoreCase = true) }
                .sortedBy { it.title.lowercase() }
                .map { game ->
                    SoundtrackGameEntry(
                        selection = MusicSelection.GameSoundtrack(
                            romId = game.rommId,
                            gameId = game.id,
                            title = game.title
                        ),
                        platformName = platformDao.getById(game.platformId)?.name,
                        coverPath = game.coverPath?.takeIf { it.isNotBlank() },
                        trackCount = countsByGame[game.id] ?: 0
                    )
                }
        }
        return SoundtrackGamePage(entries, hasMore = false, fromServer = false, serverStatus = status)
    }
}

internal fun isPlayableLocal(localPath: String?, durationSeconds: Double?): Boolean =
    (durationSeconds ?: 0.0) >= BGM_MIN_DURATION_SECONDS &&
        localPath != null &&
        File(localPath).isFile
