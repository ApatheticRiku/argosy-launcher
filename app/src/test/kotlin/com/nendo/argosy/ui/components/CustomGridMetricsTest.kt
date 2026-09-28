package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.GridAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 0.001f

class CustomGridMetricsTest {

    private fun metrics(
        width: Float,
        height: Float,
        columns: GridAxis,
        rows: GridAxis,
        gap: Float = 0f
    ) = customGridMetrics(width, height, columns, rows, gap, overhangLanes = 0f)

    @Test
    fun `fixed rows fill a landscape page with square columns`() {
        val grid = metrics(1000f, 500f, GridAxis.Fill, GridAxis.Fixed(2))

        assertEquals(4, grid.columns)
        assertEquals(2, grid.rows)
        assertEquals(250f, grid.cellWidthPx, DELTA)
        assertEquals(250f, grid.cellHeightPx, DELTA)
    }

    @Test
    fun `fixed columns fill a portrait page with square rows`() {
        val grid = metrics(500f, 1000f, GridAxis.Fixed(2), GridAxis.Fill)

        assertEquals(2, grid.columns)
        assertEquals(4, grid.rows)
        assertEquals(250f, grid.cellWidthPx, DELTA)
        assertEquals(250f, grid.cellHeightPx, DELTA)
    }

    @Test
    fun `fixed columns on a landscape page keep one row on screen and centre the block`() {
        val grid = metrics(1000f, 200f, GridAxis.Fixed(2), GridAxis.Fill)

        assertEquals(1, grid.rows)
        assertEquals(200f, grid.cellWidthPx, DELTA)
        assertEquals(300f, grid.offsetXPx, DELTA)
    }

    @Test
    fun `fixed rows on a portrait page centre the leftover height`() {
        val grid = metrics(300f, 1000f, GridAxis.Fill, GridAxis.Fixed(2))

        assertEquals(1, grid.columns)
        assertEquals(300f, grid.cellHeightPx, DELTA)
        assertEquals(200f, grid.offsetYPx, DELTA)
    }

    @Test
    fun `fixed by fixed within the stretch limit fills the page`() {
        val grid = metrics(1000f, 500f, GridAxis.Fixed(3), GridAxis.Fixed(2))

        assertEquals(1000f / 3f, grid.cellWidthPx, DELTA)
        assertEquals(250f, grid.cellHeightPx, DELTA)
        assertEquals(0f, grid.offsetXPx, DELTA)
        assertEquals(0f, grid.offsetYPx, DELTA)
    }

    @Test
    fun `fixed by fixed clamps a wide cell to four by three and centres the rest`() {
        val grid = metrics(1000f, 500f, GridAxis.Fixed(4), GridAxis.Fixed(4))

        assertEquals(125f * MAX_CELL_STRETCH, grid.cellWidthPx, DELTA)
        assertEquals(125f, grid.cellHeightPx, DELTA)
        assertEquals((1000f - 4 * 125f * MAX_CELL_STRETCH) / 2f, grid.offsetXPx, DELTA)
        assertEquals(0f, grid.offsetYPx, DELTA)
    }

    @Test
    fun `fixed by fixed clamps a tall cell to three by four and centres the rest`() {
        val grid = metrics(500f, 1000f, GridAxis.Fixed(4), GridAxis.Fixed(4))

        assertEquals(125f, grid.cellWidthPx, DELTA)
        assertEquals(125f * MAX_CELL_STRETCH, grid.cellHeightPx, DELTA)
        assertEquals(0f, grid.offsetXPx, DELTA)
        assertEquals((1000f - 4 * 125f * MAX_CELL_STRETCH) / 2f, grid.offsetYPx, DELTA)
    }

    @Test
    fun `the gap is counted between cells and never around them`() {
        val grid = metrics(1030f, 500f, GridAxis.Fixed(4), GridAxis.Fixed(2), gap = 10f)

        assertEquals(250f, grid.cellWidthPx, DELTA)
        assertEquals(245f, grid.cellHeightPx, DELTA)
        assertEquals(0f, grid.offsetXPx, DELTA)
    }

    @Test
    fun `reserved focus growth shrinks the cell on a fixed axis`() {
        val plain = metrics(1000f, 500f, GridAxis.Fill, GridAxis.Fixed(2))
        val reserved = customGridMetrics(1000f, 500f, GridAxis.Fill, GridAxis.Fixed(2), 0f, overhangLanes = 0.5f)

        assertTrue(reserved.cellHeightPx < plain.cellHeightPx)
        assertEquals(200f, reserved.cellHeightPx, DELTA)
    }

    @Test
    fun `two open axes fall back to the default row count`() {
        val grid = metrics(1000f, 300f, GridAxis.Fill, GridAxis.Fill)

        assertEquals(3, grid.rows)
    }

    @Test
    fun `an unmeasured page reports the fixed count on both axes`() {
        val grid = metrics(0f, 0f, GridAxis.Fill, GridAxis.Fixed(4))

        assertEquals(4, grid.columns)
        assertEquals(4, grid.rows)
    }
}
