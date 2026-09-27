package com.nendo.argosy.domain.usecase.cache

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.clearArtOverride
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import java.io.File
import javax.inject.Inject

class RepairImageCacheUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val romMRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager
) {
    suspend fun repairCover(gameId: Long, localPath: String?): String? {
        if (localPath == null) return null
        if (!localPath.startsWith("/")) return localPath
        if (File(localPath).exists()) return localPath

        val game = gameDao.getById(gameId) ?: return null
        if (game.coverOverridePath == localPath) {
            gameDao.clearArtOverride(gameId, ArtSlot.COVER)
            return game.coverPath
        }
        if (!romMRepository.isConnected()) return null
        val rommId = game.rommId ?: return null

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val coverUrls = romMRepository.buildCoverUrls(result.data)
                if (coverUrls.isNotEmpty()) {
                    imageCacheManager.queueCoverCache(coverUrls, rommId, game.title)
                }
                coverUrls.firstOrNull()
            }
            is RomMResult.Error -> null
        }
    }

    suspend fun repairBackground(gameId: Long, localPath: String?): String? {
        if (localPath == null) return null
        if (!localPath.startsWith("/")) return localPath
        if (File(localPath).exists()) return localPath

        val game = gameDao.getById(gameId) ?: return null
        if (game.backgroundOverridePath == localPath) {
            gameDao.clearArtOverride(gameId, ArtSlot.BACKGROUND)
            return game.backgroundPath
        }
        if (!romMRepository.isConnected()) return null
        val rommId = game.rommId ?: return null

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val backgroundUrls = (
                    result.data.backgroundUrls +
                        result.data.screenshotPaths?.mapNotNull { romMRepository.buildMediaUrlPublic(it) }.orEmpty()
                ).distinct()
                if (backgroundUrls.isNotEmpty()) {
                    imageCacheManager.queueBackgroundCache(backgroundUrls, rommId, game.title)
                }
                backgroundUrls.firstOrNull()
            }
            is RomMResult.Error -> null
        }
    }

    suspend fun repairScreenshots(gameId: Long, cachedPaths: List<String>?): List<String>? {
        if (cachedPaths.isNullOrEmpty()) return null

        val anyMissing = cachedPaths.any { path ->
            path.startsWith("/") && !File(path).exists()
        }
        if (!anyMissing) return cachedPaths
        if (!romMRepository.isConnected()) return null

        val game = gameDao.getById(gameId) ?: return null
        val rommId = game.rommId ?: return null

        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val screenshotUrls = result.data.screenshotPaths?.mapNotNull { path ->
                    romMRepository.buildMediaUrlPublic(path)
                } ?: return null

                if (screenshotUrls.isNotEmpty()) {
                    imageCacheManager.queueScreenshotCache(gameId, rommId, screenshotUrls, game.title)
                }
                screenshotUrls
            }
            is RomMResult.Error -> null
        }
    }

    fun isLocalPathValid(path: String?): Boolean {
        if (path == null) return false
        if (!path.startsWith("/")) return true
        val file = File(path)
        return file.exists() && file.length() > 512
    }
}
