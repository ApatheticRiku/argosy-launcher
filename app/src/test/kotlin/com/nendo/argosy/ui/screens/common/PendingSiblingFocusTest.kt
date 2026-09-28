package com.nendo.argosy.ui.screens.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSiblingFocusTest {

    private val pending = PendingSiblingFocus(fromGameId = 1L, toGameId = 2L, focusedIndex = 0)

    @Test
    fun `a list that swapped the old member for the shown one settles on the shown one`() {
        val (index, remaining) = pending.resolve(listOf(3L, 2L))

        assertEquals(1, index)
        assertNull(remaining)
    }

    @Test
    fun `a list still holding only the old member keeps waiting`() {
        val (index, remaining) = pending.resolve(listOf(1L, 3L))

        assertNull(index)
        assertEquals(pending, remaining)
    }

    @Test
    fun `a list holding both members focuses the shown one and keeps waiting at its index`() {
        val (index, remaining) = pending.resolve(listOf(1L, 3L, 2L))

        assertEquals(2, index)
        assertEquals(pending.copy(focusedIndex = 2), remaining)
    }

    @Test
    fun `a list holding neither member settles with nowhere to move`() {
        val (index, remaining) = pending.resolve(listOf(5L, 7L))

        assertNull(index)
        assertNull(remaining)
    }

    @Test
    fun `the cursor left where the pick found it still holds the move`() {
        assertTrue(pending.isHeldBy(focusedIndex = 0, focusedGameId = 3L))
        assertTrue(pending.isHeldBy(focusedIndex = 4, focusedGameId = 2L))
    }

    @Test
    fun `the cursor moved elsewhere drops the move`() {
        assertFalse(pending.isHeldBy(focusedIndex = 4, focusedGameId = 7L))
    }
}
