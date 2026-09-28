package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollPlacementTest {

    @Test
    fun `an empty vertical canvas starts at the top-left`() {
        assertEquals(TileRect(0, 0), firstFreeScrollRect(emptyList(), 4, HomeScrollAxis.VERTICAL, 1, 1))
    }

    @Test
    fun `a vertical canvas fills a row across before moving down`() {
        val taken = listOf(TileRect(0, 0), TileRect(1, 0), TileRect(2, 0))

        assertEquals(TileRect(3, 0), firstFreeScrollRect(taken, 4, HomeScrollAxis.VERTICAL, 1, 1))
        assertEquals(TileRect(0, 1), firstFreeScrollRect(taken, 3, HomeScrollAxis.VERTICAL, 1, 1))
    }

    @Test
    fun `a horizontal canvas fills a column down before moving right`() {
        val taken = listOf(TileRect(0, 0), TileRect(0, 1))

        assertEquals(TileRect(0, 2), firstFreeScrollRect(taken, 3, HomeScrollAxis.HORIZONTAL, 1, 1))
        assertEquals(TileRect(1, 0), firstFreeScrollRect(taken, 2, HomeScrollAxis.HORIZONTAL, 1, 1))
    }

    @Test
    fun `a gap earlier on the canvas is used before the end`() {
        val taken = listOf(TileRect(0, 0), TileRect(0, 1), TileRect(1, 0), TileRect(0, 5))

        assertEquals(TileRect(1, 1), firstFreeScrollRect(taken, 2, HomeScrollAxis.VERTICAL, 1, 1))
    }

    @Test
    fun `a span wider than the lanes is kept inside them`() {
        val placed = firstFreeScrollRect(
            emptyList(),
            1,
            HomeScrollAxis.VERTICAL,
            MEDIA_TILE_MIN_SPAN,
            MEDIA_TILE_MIN_SPAN
        )

        assertEquals(TileRect(0, 0, columnSpan = 1, rowSpan = MEDIA_TILE_MIN_SPAN), placed)
    }

    @Test
    fun `a rectangle keeps different spans on each axis`() {
        val vertical = firstFreeScrollRect(emptyList(), 4, HomeScrollAxis.VERTICAL, 3, 1)
        val horizontal = firstFreeScrollRect(emptyList(), 4, HomeScrollAxis.HORIZONTAL, 3, 1)

        assertEquals(TileRect(0, 0, columnSpan = 3, rowSpan = 1), vertical)
        assertEquals(TileRect(0, 0, columnSpan = 3, rowSpan = 1), horizontal)
    }

    @Test
    fun `settling on a horizontal canvas relocates to the earliest column`() {
        val editing = HomeTile(1L, 0, TileRect(0, 0, 1, 2), HomeTileTargetRef.Game(1L))
        val blocker = HomeTile(2L, 0, TileRect(0, 1), HomeTileTargetRef.Game(2L))
        val walls = listOf(TileRect(1, 0), TileRect(1, 1), TileRect(2, 0), TileRect(3, 0))
            .mapIndexed { index, rect -> HomeTile(10L + index, 0, rect, HomeTileTargetRef.Game(10L + index)) }

        val settled = settleAfterEdit(editing, listOf(blocker) + walls, SCROLL_AXIS_BOUND, 2)

        assertEquals(TileRect(2, 1), settled.placed.first { it.id == 2L }.rect)
    }
}
