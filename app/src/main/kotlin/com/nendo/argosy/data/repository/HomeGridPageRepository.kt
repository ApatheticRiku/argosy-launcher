package com.nendo.argosy.data.repository

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.HomeGridPageDao
import com.nendo.argosy.data.local.entity.HomeGridPageEntity
import com.nendo.argosy.data.local.entity.PageAudioKind
import com.nendo.argosy.data.local.entity.PageBackgroundKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings a curated grid page carries: what it shows behind the tiles and what it does about
 * sound. A page row is created on demand, so a page that has never been given either costs nothing.
 */
@Singleton
class HomeGridPageRepository @Inject constructor(
    private val pageDao: HomeGridPageDao,
    private val imageCacheManager: ImageCacheManager
) {

    /**
     * A [PageBackgroundKind.GAME_ART] page with a game and no stored path follows that game's
     * displayed background; the emitted row carries the resolved path.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePages(ownerUserId: Long?): Flow<List<HomeGridPageEntity>> =
        pageDao.observeAll(ownerUserId).flatMapLatest { pages ->
            val followedGameIds = pages.filter { it.followsGameBackground }
                .mapNotNull { it.backgroundGameId }
                .distinct()
            if (followedGameIds.isEmpty()) {
                flowOf(pages)
            } else {
                combine(followedGameIds.map { id -> imageCacheManager.observeArt(id).map { id to it } }) { art ->
                    val backgrounds = art.associate { (id, resolved) -> id to resolved?.backgroundPath }
                    pages.map { page ->
                        if (page.followsGameBackground) {
                            page.copy(backgroundPath = backgrounds[page.backgroundGameId])
                        } else {
                            page
                        }
                    }
                }
            }
        }

    private val HomeGridPageEntity.followsGameBackground: Boolean
        get() = backgroundKind == PageBackgroundKind.GAME_ART.name &&
            backgroundGameId != null &&
            backgroundPath == null

    suspend fun pageAt(ownerUserId: Long?, sortOrder: Int): HomeGridPageEntity? =
        pageDao.getAt(ownerUserId, sortOrder)

    /**
     * The row for a page, created if this is the first setting it has been given.
     */
    private suspend fun ensurePage(ownerUserId: Long?, sortOrder: Int): HomeGridPageEntity {
        pageDao.getAt(ownerUserId, sortOrder)?.let { return it }
        val created = HomeGridPageEntity(ownerUserId = ownerUserId, sortOrder = sortOrder)
        val id = pageDao.insert(created)
        return created.copy(id = id)
    }

    suspend fun setBackground(
        ownerUserId: Long?,
        sortOrder: Int,
        kind: PageBackgroundKind,
        path: String? = null,
        gameId: Long? = null
    ) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(
            page.copy(
                backgroundKind = kind.name,
                backgroundPath = path,
                backgroundGameId = gameId
            )
        )
    }

    suspend fun setAudio(
        ownerUserId: Long?,
        sortOrder: Int,
        kind: PageAudioKind,
        path: String? = null
    ) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(page.copy(audioKind = kind.name, audioPath = path))
    }

    suspend fun setName(ownerUserId: Long?, sortOrder: Int, name: String?) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(page.copy(name = name?.takeIf { it.isNotBlank() }))
    }

    /**
     * Drops the settings for a removed page and closes the gap, so the page after it does not
     * inherit a background it was never given.
     */
    suspend fun removePage(ownerUserId: Long?, sortOrder: Int) {
        pageDao.getAt(ownerUserId, sortOrder)?.let { pageDao.delete(it) }
        pageDao.closeGapAfter(ownerUserId, sortOrder)
    }
}
