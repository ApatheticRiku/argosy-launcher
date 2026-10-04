package com.nendo.argosy.data.repository

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.CollectionStats
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

private const val OVERVIEW_COVER_LIMIT = 32
private const val OVERVIEW_SETTLE_MS = 300L
private const val OVERVIEW_KEEP_ALIVE_MS = 60_000L

data class CollectionOverview(
    val collections: List<CollectionEntity>,
    val statsById: Map<Long, CollectionStats>,
    val coverPathsById: Map<Long, List<String>>
)

@Singleton
class CollectionOverviewSource @Inject constructor(
    collectionDao: CollectionDao,
    imageCacheManager: ImageCacheManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "CollectionOverview")

    @OptIn(FlowPreview::class)
    val overview: Flow<CollectionOverview> = combine(
        collectionDao.observeByTypes(CollectionType.entries),
        collectionDao.observeLocalCollectionStats(),
        collectionDao.observeLocalCoverGameIds(OVERVIEW_COVER_LIMIT),
        imageCacheManager.gameArt
    ) { collections, stats, coverGames, art ->
        CollectionOverview(
            collections = collections,
            statsById = stats.associateBy { it.collectionId },
            coverPathsById = coverGames
                .mapNotNull { row -> art[row.gameId]?.coverPath?.let { row.collectionId to it } }
                .groupBy({ it.first }, { it.second })
        )
    }
        .debounce(OVERVIEW_SETTLE_MS)
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(OVERVIEW_KEEP_ALIVE_MS), replay = 1)
}
