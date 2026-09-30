package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.repository.CollectionOverviewSource
import com.nendo.argosy.domain.model.CollectionSummary
import com.nendo.argosy.domain.model.toSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CategoryWithCount(
    val name: String,
    val summary: CollectionSummary,
    val coverPaths: List<String> = emptyList()
)

class GetVirtualCollectionCategoriesUseCase @Inject constructor(
    private val overviewSource: CollectionOverviewSource
) {
    fun getGenres(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GENRE)
    }

    fun getGameModes(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GAME_MODE)
    }

    fun getSeries(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.SERIES)
    }

    private fun getCategoriesByType(type: CollectionType): Flow<List<CategoryWithCount>> =
        overviewSource.overview.map { overview ->
            overview.collections
                .filter { it.type == type }
                .mapNotNull { collection ->
                    val summary = overview.statsById[collection.id].toSummary()
                    if (summary.gameCount == 0) return@mapNotNull null
                    CategoryWithCount(
                        name = collection.name,
                        summary = summary,
                        coverPaths = overview.coverPathsById[collection.id].orEmpty()
                    )
                }
                .sortedBy { it.name }
        }.distinctUntilChanged()
}
