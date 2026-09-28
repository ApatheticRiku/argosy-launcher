package com.nendo.argosy.domain.model

data class SiblingMember(
    val gameId: Long,
    val isHack: Boolean,
    val isRommMain: Boolean,
    val isPreRelease: Boolean,
    val isTranslation: Boolean,
    val regions: List<String>,
    val fileName: String
)

object SiblingGroupRanking {

    fun visibleMembers(
        members: List<SiblingMember>,
        localPickGameId: Long?,
        regionPriority: List<String>
    ): Set<Long> {
        if (members.size <= 1) return members.mapTo(mutableSetOf()) { it.gameId }

        val order = memberOrder(regionPriority)
        val representative = members.firstOrNull { it.gameId == localPickGameId }
            ?: members.filter { it.isRommMain }.minWithOrNull(order)
            ?: members.filterNot { it.isHack }.minWithOrNull(order)

        val hacks = members.filter { it.isHack }.map { it.gameId }
        return (listOfNotNull(representative?.gameId) + hacks).toSet()
    }

    private fun memberOrder(regionPriority: List<String>): Comparator<SiblingMember> =
        compareBy<SiblingMember> { if (it.isPreRelease || it.isTranslation) 1 else 0 }
            .thenBy { regionRank(it.regions, regionPriority) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }
            .thenBy { it.gameId }

    private fun regionRank(regions: List<String>, regionPriority: List<String>): Int =
        regions.mapNotNull { region ->
            regionPriority.indexOfFirst { it.equals(region, ignoreCase = true) }.takeIf { it >= 0 }
        }.minOrNull() ?: regionPriority.size
}
