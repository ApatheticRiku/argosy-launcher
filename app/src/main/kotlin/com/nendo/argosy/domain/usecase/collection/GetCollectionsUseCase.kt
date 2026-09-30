package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.repository.CollectionOverviewSource
import com.nendo.argosy.domain.model.CollectionSummary
import com.nendo.argosy.domain.model.toSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val summary: CollectionSummary,
    val coverPaths: List<String>,
    val isUserCreated: Boolean,
    val rommId: Long?
)

class GetCollectionsUseCase @Inject constructor(
    private val overviewSource: CollectionOverviewSource
) {
    operator fun invoke(): Flow<List<CollectionWithCount>> =
        overviewSource.overview.map { overview ->
            overview.collections
                .filter { it.type == CollectionType.REGULAR || it.type == CollectionType.SMART }
                .filter { it.name.isNotBlank() && it.name.lowercase() != "favorites" }
                .map { collection ->
                    CollectionWithCount(
                        id = collection.id,
                        name = collection.name,
                        description = collection.description,
                        summary = overview.statsById[collection.id].toSummary(),
                        coverPaths = overview.coverPathsById[collection.id].orEmpty(),
                        isUserCreated = collection.isUserCreated,
                        rommId = collection.rommId
                    )
                }
        }.distinctUntilChanged()
}
