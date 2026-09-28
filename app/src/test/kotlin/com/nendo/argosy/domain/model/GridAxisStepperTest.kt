package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GridAxisStepperTest {

    private val measured = CustomGridShape(columns = 7, rows = 4)

    @Test
    fun `the stepper reads scroll, fill, then two through twelve`() {
        assertEquals(
            listOf(GridAxis.Scroll, GridAxis.Fill) + (2..12).map { GridAxis.Fixed(it) },
            GRID_AXIS_STEPS
        )
    }

    @Test
    fun `stepping walks the order and clamps at both ends`() {
        assertEquals(GridAxis.Fill, GridAxis.Fixed(2).stepped(-1))
        assertEquals(GridAxis.Scroll, GridAxis.Fill.stepped(-1))
        assertEquals(GridAxis.Scroll, GridAxis.Scroll.stepped(-1))
        assertEquals(GridAxis.Fill, GridAxis.Scroll.stepped(1))
        assertEquals(GridAxis.Fixed(2), GridAxis.Fill.stepped(1))
        assertEquals(GridAxis.Fixed(12), GridAxis.Fixed(12).stepped(1))
        assertEquals(GridAxis.Fixed(12), GridAxis.Fixed(11).stepped(1))
    }

    @Test
    fun `each axis sits at its own stepper position`() {
        assertEquals(0, GridAxis.Scroll.stepIndex)
        assertEquals(1, GridAxis.Fill.stepIndex)
        assertEquals(2, GridAxis.Fixed(2).stepIndex)
    }

    @Test
    fun `scrolling the rows while the columns fill locks the columns to their measured count`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(2))

        val stepped = config.withRows(GridAxis.Scroll, measured)

        assertEquals(GridAxis.Fixed(7), stepped.columns)
        assertEquals(GridAxis.Scroll, stepped.rows)
        assertEquals(HomeScrollAxis.VERTICAL, stepped.scrollAxis)
        assertEquals(HomeGridKind.SCROLL, stepped.gridKind)
    }

    @Test
    fun `filling the columns while the rows scroll locks the rows to their measured count`() {
        val config = CustomGridConfig(columns = GridAxis.Fixed(3), rows = GridAxis.Scroll)

        val stepped = config.withColumns(GridAxis.Fill, measured)

        assertEquals(GridAxis.Fill, stepped.columns)
        assertEquals(GridAxis.Fixed(4), stepped.rows)
        assertEquals(HomeGridKind.PAGED, stepped.gridKind)
    }

    @Test
    fun `scrolling one axis beside a fixed one leaves the fixed one alone`() {
        val config = CustomGridConfig(columns = GridAxis.Fixed(5), rows = GridAxis.Fixed(3))

        val columnsScroll = config.withColumns(GridAxis.Scroll, measured)
        val rowsScroll = config.withRows(GridAxis.Scroll, measured)

        assertEquals(GridAxis.Fixed(3), columnsScroll.rows)
        assertEquals(HomeScrollAxis.HORIZONTAL, columnsScroll.scrollAxis)
        assertEquals(GridAxis.Fixed(5), rowsScroll.columns)
        assertEquals(HomeScrollAxis.VERTICAL, rowsScroll.scrollAxis)
    }

    @Test
    fun `a scrolling layout counts its lanes across the fixed axis`() {
        val vertical = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Scroll)
        val horizontal = CustomGridConfig(columns = GridAxis.Scroll, rows = GridAxis.Fixed(3))

        assertEquals(4, vertical.layoutFor(null).lanes)
        assertEquals(HomeGridKind.SCROLL, vertical.layoutFor(null).kind)
        assertEquals(3, horizontal.layoutFor(null).lanes)
        assertEquals(HomeGridKind.PAGED, CustomGridConfig().layoutFor(null).kind)
    }

    @Test
    fun `opening the columns while the rows are open locks the rows to their measured count`() {
        val config = CustomGridConfig(columns = GridAxis.Fixed(2), rows = GridAxis.Fill)

        val stepped = config.withColumns(GridAxis.Fill, measured)

        assertEquals(GridAxis.Fill, stepped.columns)
        assertEquals(GridAxis.Fixed(4), stepped.rows)
    }

    @Test
    fun `opening the rows while the columns are open locks the columns to their measured count`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(2))

        val stepped = config.withRows(GridAxis.Fill, measured)

        assertEquals(GridAxis.Fixed(7), stepped.columns)
        assertEquals(GridAxis.Fill, stepped.rows)
    }

    @Test
    fun `opening an axis beside a fixed one leaves the fixed one alone`() {
        val config = CustomGridConfig(columns = GridAxis.Fixed(5), rows = GridAxis.Fixed(3))

        assertEquals(GridAxis.Fixed(3), config.withColumns(GridAxis.Fill, measured).rows)
        assertEquals(GridAxis.Fixed(5), config.withRows(GridAxis.Fill, measured).columns)
    }

    @Test
    fun `fixing an axis never touches the other`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(3))

        val stepped = config.withRows(GridAxis.Fixed(4), measured)

        assertEquals(GridAxis.Fill, stepped.columns)
        assertEquals(GridAxis.Fixed(4), stepped.rows)
    }

    @Test
    fun `a locked count is kept inside the stepper range`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(3))

        val stepped = config.withRows(GridAxis.Fill, CustomGridShape(columns = 30, rows = 3))

        assertEquals(GridAxis.Fixed(MAX_GRID_AXIS_COUNT), stepped.columns)
    }

    @Test
    fun `a shape measured for these axes is the placement shape`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(3))
        val resolved = ResolvedGridShape(GridAxis.Fill, GridAxis.Fixed(3), CustomGridShape(6, 3))

        assertEquals(CustomGridShape(6, 3), config.shapeFor(resolved))
    }

    @Test
    fun `a shape measured for other axes is ignored and the open axis borrows the fixed count`() {
        val config = CustomGridConfig(columns = GridAxis.Fill, rows = GridAxis.Fixed(3))
        val stale = ResolvedGridShape(GridAxis.Fill, GridAxis.Fixed(2), CustomGridShape(9, 2))

        assertEquals(CustomGridShape(3, 3), config.shapeFor(stale))
        assertEquals(CustomGridShape(3, 3), config.shapeFor(null))
    }

    @Test
    fun `two fixed axes are their own placement shape`() {
        val config = CustomGridConfig(columns = GridAxis.Fixed(5), rows = GridAxis.Fixed(2))

        assertEquals(CustomGridShape(5, 2), config.shapeFor(null))
    }
}
