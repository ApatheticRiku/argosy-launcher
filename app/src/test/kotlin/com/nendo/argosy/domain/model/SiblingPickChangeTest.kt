package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiblingPickChangeTest {

    private val change = SiblingPickChange(memberIds = setOf(1L, 2L, 3L), shownGameId = 2L)

    private fun member(id: Long, shown: Boolean) = SiblingGroupMember(
        gameId = id,
        title = "Game",
        fileName = null,
        regions = emptyList(),
        kind = SiblingMemberKind.RELEASE,
        isDownloaded = false,
        isPicked = false,
        isShown = shown
    )

    @Test
    fun `a cursor on another member of the group moves to the shown member`() {
        assertEquals(2L, change.refocusTarget(1L))
        assertEquals(2L, change.refocusTarget(3L))
    }

    @Test
    fun `a cursor already on the shown member stays`() {
        assertNull(change.refocusTarget(2L))
    }

    @Test
    fun `a cursor outside the group stays`() {
        assertNull(change.refocusTarget(9L))
    }

    @Test
    fun `no cursor has nowhere to move`() {
        assertNull(change.refocusTarget(null))
    }

    @Test
    fun `a group's change carries every member and the shown one`() {
        val group = SiblingGroup("g", 7, listOf(member(1, shown = false), member(2, shown = true)))

        assertEquals(SiblingPickChange(setOf(1L, 2L), 2L), SiblingPickChange.of(group))
    }

    @Test
    fun `a group with no shown member announces nothing`() {
        val group = SiblingGroup("g", 7, listOf(member(1, shown = false), member(2, shown = false)))

        assertNull(SiblingPickChange.of(group))
    }
}
