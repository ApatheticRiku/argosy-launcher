package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val WIDE_TILE_ID = 1L
private const val HIDDEN_TILE_ID = 2L
private const val QUEUE_TILE_ID = 3L

/**
 * A narrower screen trims and hides tiles for display only; the stored rectangles change only when
 * the user moves or places a tile.
 */
class CustomGridWriteBackTest {

    private val repository = mockk<HomeTileRepository>(relaxed = true)

    private val wideTile = HomeTile(
        id = WIDE_TILE_ID,
        pageIndex = 0,
        rect = TileRect(0, 0, columnSpan = 4, rowSpan = 1),
        target = HomeTileTargetRef.Game(10L)
    )

    private val hiddenTile = HomeTile(
        id = HIDDEN_TILE_ID,
        pageIndex = 0,
        rect = TileRect(5, 1, columnSpan = 2, rowSpan = 1),
        target = HomeTileTargetRef.Game(11L)
    )

    private val queueTile = HomeTile(
        id = QUEUE_TILE_ID,
        pageIndex = 0,
        rect = TileRect(0, 1, columnSpan = 3, rowSpan = 1),
        target = HomeTileTargetRef.Collection(collectionId = 4L)
    )

    private var state = CustomGridState(
        tiles = listOf(wideTile, hiddenTile, queueTile),
        columns = 2,
        rows = 3,
        storedPages = 1
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
    fun `the page draws the wide tile trimmed and hides the far one`() {
        assertEquals(TileRect(0, 0, 2, 1), state.tilesOnPage(0).first { it.id == WIDE_TILE_ID }.rect)
        assertEquals(listOf(HIDDEN_TILE_ID), state.hiddenTiles.map { it.id })
    }

    @Test
    fun `arranging a trimmed tile and committing without moving writes nothing`() {
        state = state.copy(cell = GridCell(0, 0))

        coordinator.enterMoveMode()
        assertEquals(wideTile.rect, state.editingRect)
        coordinator.commitEdit()

        coVerify(exactly = 0) { repository.move(any(), any(), any()) }
    }

    @Test
    fun `moving a trimmed tile writes the rectangle the user moved it to`() {
        state = state.copy(cell = GridCell(0, 0))

        coordinator.enterMoveMode()
        coordinator.moveFocusedTile(GridDirection2D.DOWN)
        coordinator.commitEdit()

        coVerify(exactly = 1) { repository.move(match { it.id == WIDE_TILE_ID }, TileRect(0, 1, 2, 1), 0) }
    }

    @Test
    fun `naming a queue game retargets by id and moves nothing`() {
        state = state.copy(cell = GridCell(0, 1))

        coordinator.setFocusGame(42L)

        coVerify(exactly = 1) {
            repository.retarget(QUEUE_TILE_ID, HomeTileTargetRef.Collection(4L, focusGameId = 42L), emptyList())
        }
        coVerify(exactly = 0) { repository.move(any(), any(), any()) }
    }

    @Test
    fun `placing a hidden tile writes it at the cursor, trimmed to the page`() {
        state = state.copy(cell = GridCell(1, 2))

        coordinator.placeHiddenTile(hiddenTile)

        coVerify(exactly = 1) { repository.move(match { it.id == HIDDEN_TILE_ID }, TileRect(1, 2, 1, 1), 0) }
        assertEquals(GridCell(1, 2), state.cell)
    }

    @Test
    fun `choosing from the hidden list places that tile and closes the menu`() {
        state = state.copy(cell = GridCell(1, 2))

        coordinator.openHiddenTiles()
        coordinator.confirmMenu()

        assertFalse(state.showMenu)
        coVerify(exactly = 1) { repository.move(match { it.id == HIDDEN_TILE_ID }, TileRect(1, 2, 1, 1), 0) }
    }
}
