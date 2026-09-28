package com.nendo.argosy.ui.screens.common

/**
 * A cursor that sat at [focusedIndex] on [fromGameId] when a sibling pick made [toGameId] the
 * entry shown in its place.
 */
data class PendingSiblingFocus(
    val fromGameId: Long,
    val toGameId: Long,
    val focusedIndex: Int
) {
    fun isHeldBy(focusedIndex: Int, focusedGameId: Long?): Boolean =
        focusedIndex == this.focusedIndex || focusedGameId == toGameId

    fun indexIn(gameIds: List<Long>): Int? = gameIds.indexOf(toGameId).takeIf { it >= 0 }

    fun isSettledBy(gameIds: List<Long>): Boolean = fromGameId !in gameIds

    fun resolve(gameIds: List<Long>): Pair<Int?, PendingSiblingFocus?> {
        val index = indexIn(gameIds)
        val remaining = when {
            isSettledBy(gameIds) -> null
            index != null -> copy(focusedIndex = index)
            else -> this
        }
        return index to remaining
    }
}
