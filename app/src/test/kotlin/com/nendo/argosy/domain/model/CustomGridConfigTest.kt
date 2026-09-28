package com.nendo.argosy.domain.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomGridConfigTest {

    private fun storedGrid(vararg fields: Pair<String, Any>): String =
        JSONObject().put("customGrid", JSONObject().apply { fields.forEach { (k, v) -> put(k, v) } })
            .toString()

    @Test
    fun `a fresh install fills columns under three rows`() {
        val grid = HomeLayoutSettings.fromJson(null).customGrid

        assertEquals(GridAxis.Fill, grid.columns)
        assertEquals(GridAxis.Fixed(DEFAULT_LANE_COUNT), grid.rows)
    }

    @Test
    fun `a stored lane count becomes the rows under filled columns`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("laneCount" to 5, "matchOtherScreen" to false))
            .customGrid

        assertEquals(GridAxis.Fill, grid.columns)
        assertEquals(GridAxis.Fixed(5), grid.rows)
    }

    @Test
    fun `the migrated config round trips without the retired keys`() {
        val migrated = HomeLayoutSettings.fromJson(storedGrid("laneCount" to 4, "matchOtherScreen" to true))
        val written = JSONObject(migrated.toJson()).getJSONObject("customGrid")

        assertFalse(written.has("laneCount"))
        assertFalse(written.has("matchOtherScreen"))
        assertEquals(migrated, HomeLayoutSettings.fromJson(migrated.toJson()))
    }

    @Test
    fun `explicit axes win over a leftover lane count`() {
        val grid = HomeLayoutSettings.fromJson(
            storedGrid("laneCount" to 5, "columns" to 6, "rows" to "FILL")
        ).customGrid

        assertEquals(GridAxis.Fixed(6), grid.columns)
        assertEquals(GridAxis.Fill, grid.rows)
    }

    @Test
    fun `fixed and fill axes survive a round trip`() {
        val settings = HomeLayoutSettings(
            customGrid = CustomGridConfig(columns = GridAxis.Fixed(7), rows = GridAxis.Fixed(12))
        )

        assertEquals(settings.customGrid, HomeLayoutSettings.fromJson(settings.toJson()).customGrid)
    }

    @Test
    fun `a stored scroll axis reads back as scroll`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("columns" to "SCROLL", "rows" to 4)).customGrid

        assertEquals(GridAxis.Scroll, grid.columns)
        assertEquals(GridAxis.Fixed(4), grid.rows)
    }

    @Test
    fun `scroll axes survive a round trip in either direction`() {
        val vertical = HomeLayoutSettings(
            customGrid = CustomGridConfig(columns = GridAxis.Fixed(5), rows = GridAxis.Scroll)
        )
        val horizontal = HomeLayoutSettings(
            customGrid = CustomGridConfig(columns = GridAxis.Scroll, rows = GridAxis.Fixed(3))
        )

        assertEquals(vertical.customGrid, HomeLayoutSettings.fromJson(vertical.toJson()).customGrid)
        assertEquals(horizontal.customGrid, HomeLayoutSettings.fromJson(horizontal.toJson()).customGrid)
        assertEquals("SCROLL", JSONObject(vertical.toJson()).getJSONObject("customGrid").get("rows"))
    }

    @Test
    fun `scroll beside fill reads back with the rows fixed`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("columns" to "SCROLL", "rows" to "FILL")).customGrid

        assertEquals(GridAxis.Scroll, grid.columns)
        assertEquals(GridAxis.Fixed(DEFAULT_LANE_COUNT), grid.rows)
    }

    @Test
    fun `two open axes read back with the rows fixed`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("columns" to "FILL", "rows" to "FILL")).customGrid

        assertEquals(GridAxis.Fill, grid.columns)
        assertEquals(GridAxis.Fixed(DEFAULT_LANE_COUNT), grid.rows)
    }

    @Test
    fun `counts outside the stepper range are clamped`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("columns" to 40, "rows" to 1)).customGrid

        assertEquals(GridAxis.Fixed(MAX_GRID_AXIS_COUNT), grid.columns)
        assertEquals(GridAxis.Fixed(MIN_GRID_AXIS_COUNT), grid.rows)
    }

    @Test
    fun `other grid fields survive the axis migration`() {
        val grid = HomeLayoutSettings.fromJson(
            storedGrid("laneCount" to 3, "showEmptySlots" to false, "pageCount" to 4, "autoAdd" to "AUTO")
        ).customGrid

        assertFalse(grid.showEmptySlots)
        assertEquals(4, grid.pageCount)
        assertEquals(HomeTileAutoAdd.AUTO, grid.autoAdd)
    }

    @Test
    fun `the scroll arrangement survives a round trip on a paged config`() {
        val settings = HomeLayoutSettings(
            customGrid = CustomGridConfig(
                columns = GridAxis.Fill,
                rows = GridAxis.Fixed(4),
                scrollArrangement = ScrollArrangement(HomeScrollAxis.HORIZONTAL, 2)
            )
        )

        assertEquals(settings.customGrid, HomeLayoutSettings.fromJson(settings.toJson()).customGrid)
    }

    @Test
    fun `a config written before the arrangement existed reads back without one`() {
        val grid = HomeLayoutSettings.fromJson(storedGrid("columns" to "SCROLL", "rows" to 4)).customGrid

        assertEquals(null, grid.scrollArrangement)
    }

    @Test
    fun `a malformed arrangement is dropped rather than failing the grid`() {
        val grid = HomeLayoutSettings.fromJson(
            storedGrid("rows" to 4, "scrollArrangement" to JSONObject().put("scrollAxis", "DIAGONAL"))
        ).customGrid

        assertEquals(null, grid.scrollArrangement)
        assertEquals(GridAxis.Fixed(4), grid.rows)
    }
}
