package com.nendo.argosy.domain.model

/**
 * A stored or cleared pick for one sibling group on one platform: [shownGameId] is the member the
 * library shows for [memberIds] from now on.
 */
data class SiblingPickChange(
    val memberIds: Set<Long>,
    val shownGameId: Long
) {
    /**
     * The game a cursor resting on [focusedGameId] moves to, or null when that cursor is outside
     * this group or already on the shown member.
     */
    fun refocusTarget(focusedGameId: Long?): Long? =
        shownGameId.takeIf { focusedGameId != null && focusedGameId != shownGameId && focusedGameId in memberIds }

    companion object {
        fun of(group: SiblingGroup): SiblingPickChange? {
            val shown = group.shownMember ?: return null
            return SiblingPickChange(group.members.mapTo(HashSet()) { it.gameId }, shown.gameId)
        }
    }
}
