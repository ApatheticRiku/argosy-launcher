package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.CustomTileMenuAction
import com.nendo.argosy.ui.components.TilePickerEntry
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOP_TILE_ID = 1L
private const val TALL_TILE_ID = 2L
private const val DEEP_TILE_ID = 3L
private const val PAGED_TILE_ID = 9L

class CustomGridScrollModeTest {

    private val repository = mockk<HomeTileRepository>(relaxed = true)

    private fun scrollTile(id: Long, rect: TileRect) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = rect,
        target = HomeTileTargetRef.Game(id * 10),
        kind = HomeGridKind.SCROLL
    )

    private val topTile = scrollTile(TOP_TILE_ID, TileRect(0, 0))
    private val tallTile = scrollTile(TALL_TILE_ID, TileRect(1, 1, columnSpan = 1, rowSpan = 2))
    private val deepTile = scrollTile(DEEP_TILE_ID, TileRect(2, 3))
    private val pagedTile = HomeTile(
        id = PAGED_TILE_ID,
        pageIndex = 0,
        rect = TileRect(2, 0),
        target = HomeTileTargetRef.Game(90L)
    )

    private var state = CustomGridState(
        tiles = listOf(topTile, tallTile, deepTile, pagedTile),
        columns = 3,
        rows = 2,
        scrollAxis = HomeScrollAxis.VERTICAL
    )

    private val coordinator = CustomGridCoordinator(
        context = mockk<Context>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        repository = repository,
        ownerUserId = { null },
        pickerEntries = { _, _, _ -> emptyList() },
        read = { state },
        write = { transform -> state = transform(state) }
    )

    @Test
    fun `the cursor moves down past the rows the screen shows`() {
        state = state.copy(cell = GridCell(0, 1))

        assertTrue(coordinator.moveFocus(GridDirection2D.DOWN))
        assertTrue(coordinator.moveFocus(GridDirection2D.DOWN))

        assertEquals(GridCell(0, 3), state.cell)
        assertEquals(0, state.page)
    }

    @Test
    fun `the canvas runs one empty line past the furthest tile and stops there`() {
        state = state.copy(cell = GridCell(0, 3))

        assertEquals(5, state.scrollExtent)
        assertTrue(coordinator.moveFocus(GridDirection2D.DOWN))
        assertEquals(GridCell(0, 4), state.cell)
        assertFalse(coordinator.moveFocus(GridDirection2D.DOWN))
        assertEquals(GridCell(0, 4), state.cell)
    }

    @Test
    fun `running off the fixed axis never turns a page`() {
        state = state.copy(cell = GridCell(2, 0))

        assertFalse(coordinator.moveFocus(GridDirection2D.RIGHT))
        state = state.copy(cell = GridCell(0, 0))
        assertFalse(coordinator.moveFocus(GridDirection2D.LEFT))

        assertEquals(0, state.page)
    }

    @Test
    fun `picking up a scroll tile stored outside the lanes starts from where it is drawn`() {
        val stray = scrollTile(DEEP_TILE_ID, TileRect(5, 0))
        state = state.copy(tiles = listOf(topTile, stray))
        val drawn = state.tilesOnPage(0).first { it.id == DEEP_TILE_ID }.rect
        state = state.copy(cell = GridCell(drawn.columnIndex, drawn.rowIndex))

        coordinator.enterMoveMode()

        assertEquals(drawn, state.editingRect)
        assertTrue(coordinator.moveFocus(GridDirection2D.DOWN))
    }

    @Test
    fun `page turns do nothing on a scrolling grid`() {
        assertFalse(coordinator.turnPage(1))
        assertFalse(coordinator.turnPage(-1))
        assertEquals(0, state.page)
        assertFalse(state.isOnAddPage)
        assertFalse(state.canDeletePage)
    }

    @Test
    fun `a horizontal grid scrolls right and stops at its fixed rows`() {
        state = state.copy(
            tiles = listOf(scrollTile(TOP_TILE_ID, TileRect(4, 0))),
            columns = 2,
            rows = 3,
            scrollAxis = HomeScrollAxis.HORIZONTAL,
            cell = GridCell(1, 2)
        )

        assertTrue(coordinator.moveFocus(GridDirection2D.RIGHT))
        assertEquals(GridCell(2, 2), state.cell)
        assertFalse(coordinator.moveFocus(GridDirection2D.DOWN))
        assertEquals(6, state.canvasColumns)
        assertEquals(3, state.canvasRows)
    }

    @Test
    fun `paged tiles never show on the scrolling grid`() {
        assertEquals(
            setOf(TOP_TILE_ID, TALL_TILE_ID, DEEP_TILE_ID),
            state.tilesOnPage(0).map { it.id }.toSet()
        )
        assertEquals(null, state.tileAt(GridCell(2, 0)))
    }

    @Test
    fun `scroll tiles never show on the paged grid`() {
        state = state.copy(scrollAxis = null)

        assertEquals(listOf(PAGED_TILE_ID), state.tilesOnPage(0).map { it.id })
    }

    @Test
    fun `a tile far down the canvas is drawn whole and never hidden`() {
        val farTile = scrollTile(DEEP_TILE_ID, TileRect(0, 40, columnSpan = 2, rowSpan = 3))
        state = state.copy(tiles = listOf(farTile))

        assertEquals(farTile.rect, state.tilesOnPage(0).single().rect)
        assertTrue(state.hiddenTiles.isEmpty())
    }

    @Test
    fun `fewer lanes than the stored tiles need hides nothing and offers no hidden list`() {
        state = state.copy(columns = 2, cell = GridCell(0, 0))

        val shown = state.tilesOnPage(0)

        assertEquals(setOf(TOP_TILE_ID, TALL_TILE_ID, DEEP_TILE_ID), shown.map { it.id }.toSet())
        assertTrue(shown.all { it.rect.lastColumn < 2 })
        assertTrue(state.hiddenTiles.isEmpty())
        assertFalse(CustomTileMenuAction.HIDDEN_TILES in state.menuActions)
    }

    @Test
    fun `a tile moves down past the screen and is stored where it landed`() {
        state = state.copy(cell = GridCell(0, 0))

        coordinator.enterMoveMode()
        repeat(4) { assertTrue(coordinator.moveFocusedTile(GridDirection2D.DOWN)) }
        coordinator.commitEdit()

        coVerify(exactly = 1) {
            repository.move(match { it.id == TOP_TILE_ID }, TileRect(0, 4), 0)
        }
    }

    @Test
    fun `a tile pushed off the fixed axis stays put rather than changing page`() {
        state = state.copy(cell = GridCell(0, 0))

        coordinator.enterMoveMode()

        assertFalse(coordinator.moveFocusedTile(GridDirection2D.LEFT))
        assertEquals(0, state.page)
        assertEquals(TileRect(0, 0), state.editingRect)
    }

    @Test
    fun `a tile grows down past the rows the screen shows`() {
        state = state.copy(cell = GridCell(2, 3))

        coordinator.enterMoveMode()
        coordinator.toggleEditMode()
        repeat(3) { assertTrue(coordinator.resizeFocusedTile(GridDirection2D.DOWN)) }

        assertEquals(TileRect(2, 3, 1, 4), state.editingRect)
    }

    @Test
    fun `placing on an empty cell writes a scroll tile`() {
        state = state.copy(cell = GridCell(0, 4))

        coordinator.placeOnFocusedCell(HomeTileTargetRef.Game(5L))

        coVerify(exactly = 1) {
            repository.place(null, HomeGridKind.SCROLL, 0, TileRect(0, 4), HomeTileTargetRef.Game(5L), emptyList())
        }
    }

    @Test
    fun `accepting a download offer appends to the scrolling grid`() {
        val target = HomeTileTargetRef.Game(7L)
        coordinator.showPendingAdd(TilePickerEntry(target = target, title = "", subtitle = ""))

        coordinator.confirmPendingAdd { }

        coVerify(exactly = 1) {
            repository.append(
                null,
                target,
                CustomGridLayout(CustomGridShape(3, 2), HomeScrollAxis.VERTICAL),
                emptyList()
            )
        }
    }

    @Test
    fun `the tile menu offers no page rows`() {
        state = state.copy(cell = GridCell(0, 0))

        val actions = state.menuActions

        assertFalse(CustomTileMenuAction.PAGE_BACKDROP in actions)
        assertFalse(CustomTileMenuAction.PAGE_MUSIC in actions)
        assertFalse(CustomTileMenuAction.DELETE_PAGE in actions)
        assertTrue(CustomTileMenuAction.ARRANGE in actions)
    }

    @Test
    fun `a tile spanning lines keeps those lines in one band`() {
        assertEquals(listOf(0..0, 1..2, 3..3, 4..4), state.scrollBands)
        state = state.copy(cell = GridCell(0, 2))
        assertEquals(1, state.focusedBandIndex)
    }

    @Test
    fun `switching kinds puts the cursor back at the start`() {
        state = state.copy(scrollAxis = null, page = 2, cell = GridCell(1, 1))

        coordinator.applyConfig(autoFit = true, storedPages = 0, scrollAxis = HomeScrollAxis.VERTICAL)

        assertEquals(0, state.page)
        assertEquals(GridCell(0, 0), state.cell)
        assertEquals(HomeGridKind.SCROLL, state.kind)
    }
}
