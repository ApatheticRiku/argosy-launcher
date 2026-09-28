package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.MAX_GRID_AXIS_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutGridAxisFieldsTest {

    private val measured = CustomGridShape(columns = 6, rows = 3)

    private fun settings(columns: GridAxis, rows: GridAxis) =
        HomeLayoutSettings(customGrid = CustomGridConfig(columns = columns, rows = rows))

    @Test
    fun `the custom grid lists columns then rows ahead of its toggles`() {
        val fields = homeLayoutFieldsFor(HomeLayoutKind.CUSTOM_GRID)

        assertEquals(HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, fields[0])
        assertEquals(HomeLayoutSettingField.CUSTOM_GRID_ROWS, fields[1])
    }

    @Test
    fun `right from fill on columns reaches two and left returns to fill`() {
        val right = adjustHomeLayoutField(
            settings(GridAxis.Fill, GridAxis.Fixed(3)),
            HomeLayoutSettingField.CUSTOM_GRID_COLUMNS,
            1,
            measured
        )
        val left = adjustHomeLayoutField(right, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, -1, measured)

        assertEquals(GridAxis.Fixed(2), right.customGrid.columns)
        assertEquals(GridAxis.Fill, left.customGrid.columns)
    }

    @Test
    fun `stepping rows down to fill locks the open columns at the measured count`() {
        val adjusted = adjustHomeLayoutField(
            settings(GridAxis.Fill, GridAxis.Fixed(2)),
            HomeLayoutSettingField.CUSTOM_GRID_ROWS,
            -1,
            measured
        )

        assertEquals(GridAxis.Fixed(6), adjusted.customGrid.columns)
        assertEquals(GridAxis.Fill, adjusted.customGrid.rows)
    }

    @Test
    fun `stepping columns down to fill locks the open rows at the measured count`() {
        val adjusted = adjustHomeLayoutField(
            settings(GridAxis.Fixed(2), GridAxis.Fill),
            HomeLayoutSettingField.CUSTOM_GRID_COLUMNS,
            -1,
            measured
        )

        assertEquals(GridAxis.Fill, adjusted.customGrid.columns)
        assertEquals(GridAxis.Fixed(3), adjusted.customGrid.rows)
    }

    @Test
    fun `left from fill on rows reaches scroll with the columns still locked`() {
        val filled = adjustHomeLayoutField(
            settings(GridAxis.Fill, GridAxis.Fixed(2)),
            HomeLayoutSettingField.CUSTOM_GRID_ROWS,
            -1,
            measured
        )
        val scrolled = adjustHomeLayoutField(filled, HomeLayoutSettingField.CUSTOM_GRID_ROWS, -1, measured)

        assertEquals(GridAxis.Fixed(6), scrolled.customGrid.columns)
        assertEquals(GridAxis.Scroll, scrolled.customGrid.rows)
    }

    @Test
    fun `scrolling the columns while the rows fill locks the rows`() {
        val adjusted = adjustHomeLayoutField(
            settings(GridAxis.Fixed(2), GridAxis.Fill),
            HomeLayoutSettingField.CUSTOM_GRID_COLUMNS,
            -1,
            measured
        )
        val scrolled = adjustHomeLayoutField(adjusted, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, -1, measured)

        assertEquals(GridAxis.Scroll, scrolled.customGrid.columns)
        assertEquals(GridAxis.Fixed(3), scrolled.customGrid.rows)
    }

    @Test
    fun `the blank pages row is hidden while the grid scrolls`() {
        val paged = settings(GridAxis.Fill, GridAxis.Fixed(3)).copy(selected = HomeLayoutKind.CUSTOM_GRID)
        val scrolling = settings(GridAxis.Fixed(3), GridAxis.Scroll).copy(selected = HomeLayoutKind.CUSTOM_GRID)

        assertEquals(true, isHomeLayoutFieldShown(paged, HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES))
        assertEquals(false, isHomeLayoutFieldShown(scrolling, HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES))
        assertEquals(true, isHomeLayoutFieldShown(scrolling, HomeLayoutSettingField.CUSTOM_GRID_ROWS))
    }

    @Test
    fun `a step past either end of the axis stepper reports the bound`() {
        val atEnds = settings(GridAxis.Scroll, GridAxis.Fixed(MAX_GRID_AXIS_COUNT))

        assertTrue(isHomeLayoutFieldAtBound(atEnds, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, -1))
        assertTrue(isHomeLayoutFieldAtBound(atEnds, HomeLayoutSettingField.CUSTOM_GRID_ROWS, 1))
    }

    @Test
    fun `a step away from the end of the axis stepper is not a bound`() {
        val atEnds = settings(GridAxis.Scroll, GridAxis.Fixed(MAX_GRID_AXIS_COUNT))

        assertFalse(isHomeLayoutFieldAtBound(atEnds, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, 1))
        assertFalse(isHomeLayoutFieldAtBound(atEnds, HomeLayoutSettingField.CUSTOM_GRID_ROWS, -1))
    }

    @Test
    fun `a step inside the axis stepper is not a bound in either direction`() {
        val middle = settings(GridAxis.Fill, GridAxis.Fixed(3))

        assertFalse(isHomeLayoutFieldAtBound(middle, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, -1))
        assertFalse(isHomeLayoutFieldAtBound(middle, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, 1))
        assertFalse(isHomeLayoutFieldAtBound(middle, HomeLayoutSettingField.CUSTOM_GRID_ROWS, -1))
        assertFalse(isHomeLayoutFieldAtBound(middle, HomeLayoutSettingField.CUSTOM_GRID_ROWS, 1))
    }

    @Test
    fun `a reported bound matches an adjustment that changes nothing`() {
        val atEnds = settings(GridAxis.Scroll, GridAxis.Fixed(MAX_GRID_AXIS_COUNT))
        val adjusted = adjustHomeLayoutField(atEnds, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS, -1, measured)

        assertEquals(atEnds.customGrid.columns, adjusted.customGrid.columns)
        assertEquals(atEnds.customGrid.rows, adjusted.customGrid.rows)
    }

    @Test
    fun `confirm never changes an axis`() {
        val before = settings(GridAxis.Fill, GridAxis.Fixed(3))

        assertEquals(before, toggleHomeLayoutField(before, HomeLayoutSettingField.CUSTOM_GRID_COLUMNS))
        assertEquals(before, toggleHomeLayoutField(before, HomeLayoutSettingField.CUSTOM_GRID_ROWS))
    }
}
