package com.nendo.argosy.data.cache

import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.model.ArtSlot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library sync's write into `game_art`: stores the first of [urls] as [slot]'s source url and
 * hands the slot to the image cache, which downloads only when the cached file does not already
 * come from that url. An empty [urls] clears the source and leaves any cached file in place.
 */
suspend fun recordArtSource(
    gameArtDao: GameArtDao,
    imageCacheManager: ImageCacheManager,
    gameId: Long,
    slot: ArtSlot,
    urls: List<String>,
    title: String,
    rommId: Long? = null,
    steamAppId: Long? = null
) {
    gameArtDao.setSourceUrl(gameId, slot, urls.firstOrNull())
    if (urls.isEmpty()) return
    imageCacheManager.queueArtIfStale(gameId, slot, urls, rommId, steamAppId, title)
}

@Singleton
class ArtSourceRecorder @Inject constructor(
    private val gameArtDao: GameArtDao,
    private val imageCacheManager: ImageCacheManager
) {
    suspend fun record(
        gameId: Long,
        slot: ArtSlot,
        urls: List<String>,
        title: String,
        rommId: Long? = null,
        steamAppId: Long? = null
    ) = recordArtSource(gameArtDao, imageCacheManager, gameId, slot, urls, title, rommId, steamAppId)
}
