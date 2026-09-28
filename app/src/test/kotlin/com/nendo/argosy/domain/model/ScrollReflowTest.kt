package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollReflowTest {

    private fun game(id: Long, rect: TileRect) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = rect,
        target = HomeTileTargetRef.Game(id),
        kind = HomeGridKind.SCROLL
    )

    private fun media(id: Long, rect: TileRect) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = rect,
        target = HomeTileTargetRef.LocalMedia("/v$id.mp4"),
        kind = HomeGridKind.SCROLL
    )

    private fun vertical(lanes: Int) =
        CustomGridLayout(CustomGridShape(columns = lanes, rows = 3), HomeScrollAxis.VERTICAL)

    private fun horizontal(lanes: Int) =
        CustomGridLayout(CustomGridShape(columns = 3, rows = lanes), HomeScrollAxis.HORIZONTAL)

    private val paged = CustomGridLayout(CustomGridShape(columns = 4, rows = 3))

    private fun List<HomeTile>.rects(): Map<Long, TileRect> = associate { it.id to it.rect }

    @Test
    fun `fewer columns on a vertical grid repack in row-then-column order`() {
        val stored = listOf(
            game(5, TileRect(0, 1)),
            game(3, TileRect(2, 0)),
            game(1, TileRect(0, 0)),
            game(4, TileRect(3, 0)),
            game(2, TileRect(1, 0))
        )

        val reflowed = reflowScrollTiles(stored, vertical(4), vertical(3))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), reflowed.map { it.id })
        assertEquals(
            mapOf(
                1L to TileRect(0, 0),
                2L to TileRect(1, 0),
                3L to TileRect(2, 0),
                4L to TileRect(0, 1),
                5L to TileRect(1, 1)
            ),
            reflowed.rects()
        )
    }

    @Test
    fun `flipping rows to columns fills each new column down in the old reading order`() {
        val stored = listOf(
            game(1, TileRect(0, 0)),
            game(2, TileRect(1, 0)),
            game(3, TileRect(2, 0)),
            game(4, TileRect(0, 1))
        )

        val reflowed = reflowScrollTiles(stored, vertical(3), horizontal(2))

        assertEquals(
            mapOf(
                1L to TileRect(0, 0),
                2L to TileRect(0, 1),
                3L to TileRect(1, 0),
                4L to TileRect(1, 1)
            ),
            reflowed.rects()
        )
    }

    @Test
    fun `a horizontal grid is read column by column`() {
        val stored = listOf(
            game(1, TileRect(0, 0)),
            game(2, TileRect(0, 1)),
            game(3, TileRect(1, 0)),
            game(4, TileRect(1, 1))
        )

        val reflowed = reflowScrollTiles(stored, horizontal(2), vertical(3))

        assertEquals(
            mapOf(
                1L to TileRect(0, 0),
                2L to TileRect(1, 0),
                3L to TileRect(2, 0),
                4L to TileRect(0, 1)
            ),
            reflowed.rects()
        )
    }

    @Test
    fun `a flip keeps a wide tile wide`() {
        val stored = listOf(
            game(1, TileRect(0, 0, columnSpan = 2, rowSpan = 1)),
            game(2, TileRect(2, 0))
        )

        val reflowed = reflowScrollTiles(stored, vertical(3), horizontal(3))

        assertEquals(TileRect(0, 0, columnSpan = 2, rowSpan = 1), reflowed.rects()[1L])
        assertEquals(TileRect(0, 1), reflowed.rects()[2L])
    }

    @Test
    fun `a span wider than the new lanes is cut to them`() {
        val stored = listOf(game(1, TileRect(0, 0, columnSpan = 3, rowSpan = 2)))

        val reflowed = reflowScrollTiles(stored, vertical(4), vertical(2))

        assertEquals(TileRect(0, 0, columnSpan = 2, rowSpan = 2), reflowed.single().rect)
    }

    @Test
    fun `a media tile is never cut below its minimum span`() {
        val stored = listOf(media(1, TileRect(0, 0, columnSpan = 3, rowSpan = 2)))

        val reflowed = reflowScrollTiles(stored, vertical(4), vertical(2))

        assertEquals(TileRect(0, 0, MEDIA_TILE_MIN_SPAN, MEDIA_TILE_MIN_SPAN), reflowed.single().rect)
    }

    @Test
    fun `a media tile wider than the lanes allow is clamped to the lanes`() {
        val stored = listOf(media(1, TileRect(0, 0, columnSpan = 2, rowSpan = 2)))
        val oneLane = CustomGridLayout(CustomGridShape(columns = 1, rows = 3), HomeScrollAxis.VERTICAL)

        val reflowed = reflowScrollTiles(stored, vertical(3), oneLane)

        assertEquals(TileRect(0, 0, columnSpan = 1, rowSpan = MEDIA_TILE_MIN_SPAN), reflowed.single().rect)
    }

    @Test
    fun `entering or leaving scroll mode is never a reflow`() {
        val stored = listOf(game(1, TileRect(3, 0)))

        assertFalse(paged.needsScrollReflowTo(vertical(2)))
        assertFalse(vertical(2).needsScrollReflowTo(paged))
        assertSame(stored, reflowScrollTiles(stored, paged, vertical(2)))
        assertSame(stored, reflowScrollTiles(stored, vertical(4), paged))
    }

    @Test
    fun `only a changed axis or lane count is a reflow`() {
        assertFalse(vertical(3).needsScrollReflowTo(vertical(3)))
        assertTrue(vertical(3).needsScrollReflowTo(vertical(4)))
        assertTrue(vertical(3).needsScrollReflowTo(horizontal(3)))
    }

    @Test
    fun `drawing a scroll grid moves tiles past the lanes onto free cells instead of hiding them`() {
        val stored = listOf(
            game(1, TileRect(0, 0)),
            game(2, TileRect(3, 0)),
            game(3, TileRect(1, 0, columnSpan = 3, rowSpan = 1))
        )

        val placed = placeScrollTiles(stored, lanes = 2, axis = HomeScrollAxis.VERTICAL)

        assertEquals(listOf(1L, 2L, 3L), placed.map { it.id })
        assertTrue(placed.all { it.rect.lastColumn < 2 })
        assertTrue(
            placed.indices.all { i -> placed.indices.none { j -> i < j && placed[i].rect.overlaps(placed[j].rect) } }
        )
        assertEquals(TileRect(0, 0), placed[0].rect)
    }
}
