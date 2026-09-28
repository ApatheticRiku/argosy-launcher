package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileTest {

    private fun tile(
        id: Long,
        column: Int,
        row: Int,
        columnSpan: Int = 1,
        rowSpan: Int = 1
    ) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = TileRect(column, row, columnSpan, rowSpan),
        target = HomeTileTargetRef.Game(id)
    )

    @Test
    fun `tiles that fit keep their span`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0, columnSpan = 2), tile(2, 2, 0)),
            columns = 3,
            rows = 3
        )

        assertEquals(2, placement.placed.size)
        assertTrue(placement.displaced.isEmpty())
        assertEquals(2, placement.placed.first().rect.columnSpan)
    }

    @Test
    fun `a span that no longer fits the page is trimmed rather than dropped`() {
        val placement = placeTiles(listOf(tile(1, 0, 0, columnSpan = 3)), columns = 2, rows = 2)

        assertEquals(1, placement.placed.size)
        assertEquals(2, placement.placed.single().rect.columnSpan)
        assertTrue(placement.displaced.isEmpty())
    }

    @Test
    fun `a span is trimmed to clear a tile already holding the cells`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0), tile(2, 1, 0, columnSpan = 3)),
            columns = 4,
            rows = 2
        )

        assertEquals(2, placement.placed.size)
        assertEquals(3, placement.placed.last().rect.columnSpan)
    }

    @Test
    fun `only a taken anchor displaces a tile`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0, columnSpan = 2), tile(2, 1, 0)),
            columns = 3,
            rows = 1
        )

        assertEquals(listOf(1L), placement.placed.map { it.id })
        assertEquals(listOf(2L), placement.displaced.map { it.id })
    }

    @Test
    fun `an anchor outside the page is displaced rather than moved`() {
        val placement = placeTiles(listOf(tile(1, 5, 0)), columns = 3, rows = 3)

        assertTrue(placement.placed.isEmpty())
        assertEquals(listOf(1L), placement.displaced.map { it.id })
    }

    @Test
    fun `a tile running off the bottom edge is clipped there and keeps its width`() {
        val placement = placeTiles(listOf(tile(1, 0, 1, columnSpan = 3, rowSpan = 3)), columns = 4, rows = 2)

        assertEquals(TileRect(0, 1, 3, 1), placement.placed.single().rect)
        assertTrue(placement.displaced.isEmpty())
    }

    @Test
    fun `an anchor below the page is hidden, never relocated`() {
        val placement = placeTiles(listOf(tile(1, 0, 0), tile(2, 1, 3)), columns = 4, rows = 3)

        assertEquals(listOf(1L), placement.placed.map { it.id })
        assertEquals(listOf(2L), placement.displaced.map { it.id })
        assertEquals(TileRect(1, 3), placement.displaced.single().rect)
    }

    @Test
    fun `a media tile clipped below its minimum span is hidden`() {
        val media = HomeTile(
            id = 9L,
            pageIndex = 0,
            rect = TileRect(3, 0, 2, 2),
            target = HomeTileTargetRef.LocalMedia("/v.mp4")
        )

        val placement = placeTiles(listOf(media), columns = 4, rows = 3)

        assertTrue(placement.placed.isEmpty())
        assertEquals(listOf(9L), placement.displaced.map { it.id })
    }

    @Test
    fun `a media tile whose minimum span still fits is clipped rather than hidden`() {
        val media = HomeTile(
            id = 9L,
            pageIndex = 0,
            rect = TileRect(2, 0, 3, 2),
            target = HomeTileTargetRef.LocalMedia("/v.mp4")
        )

        val placement = placeTiles(listOf(media), columns = 4, rows = 3)

        assertEquals(TileRect(2, 0, 2, 2), placement.placed.single().rect)
    }

    @Test
    fun `placement hands back the stored tiles it hides untouched`() {
        val stored = tile(1, 5, 5, columnSpan = 2)

        val placement = placeTiles(listOf(stored), columns = 3, rows = 3)

        assertEquals(stored, placement.displaced.single())
    }

    @Test
    fun `trimming keeps the anchor and never drops a span below one`() {
        assertEquals(TileRect(2, 1, 1, 2), TileRect(2, 1, 4, 2).trimmedTo(3, 3))
        assertEquals(TileRect(5, 0, 1, 1), TileRect(5, 0, 2, 1).trimmedTo(3, 3))
    }

    @Test
    fun `covers reports every cell under a spanning tile`() {
        val rect = TileRect(1, 1, columnSpan = 2, rowSpan = 2)

        assertTrue(rect.covers(1, 1))
        assertTrue(rect.covers(2, 2))
        assertTrue(!rect.covers(0, 1))
        assertTrue(!rect.covers(3, 1))
    }
}
