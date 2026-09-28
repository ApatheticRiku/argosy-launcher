package com.nendo.argosy.domain.model

data class SeedCandidate(
    val gameId: Long,
    val groupKey: String,
    val isDownloaded: Boolean
)

object SiblingPickSeed {

    fun picks(candidates: List<SeedCandidate>, pickedGroupKeys: Set<String>): Map<String, Long> =
        candidates
            .groupBy { it.groupKey }
            .filterKeys { it !in pickedGroupKeys }
            .filterValues { it.size > 1 }
            .mapNotNull { (groupKey, members) ->
                members.filter { it.isDownloaded }.singleOrNull()?.let { groupKey to it.gameId }
            }
            .toMap()
}
