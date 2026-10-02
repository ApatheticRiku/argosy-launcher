package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRefreshFocusTest {

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true,
        isRommGame = true,
        isSteamGame = false,
        needsInstall = false
    )

    private fun grid(focusedIndex: Int) = HomeUiState(
        layoutKind = HomeLayoutKind.AUTO_GRID,
        currentRow = HomeRow.Continue,
        recentGames = (1L..6L).map(::game),
        focusedGameIndex = focusedIndex
    )

    @Test
    fun `a cursor moved while the refresh ran keeps the game it moved to`() {
        val afterScrolling = grid(focusedIndex = 4)

        val index = afterScrolling.indexAfterRefresh(HomeRow.Continue, listOf(1L, 2L, 3L, 4L, 5L, 6L), anchorGameId = null)

        assertEquals(4, index)
    }

    @Test
    fun `the focused game is followed when the refresh reorders the row`() {
        val state = grid(focusedIndex = 4)

        val index = state.indexAfterRefresh(HomeRow.Continue, listOf(5L, 1L, 2L, 3L, 4L, 6L), anchorGameId = null)

        assertEquals(0, index)
    }

    @Test
    fun `an explicit anchor still decides where the cursor lands`() {
        val state = grid(focusedIndex = 4)

        val index = state.indexAfterRefresh(HomeRow.Continue, listOf(1L, 2L, 3L, 4L, 5L, 6L), anchorGameId = 2L)

        assertEquals(1, index)
    }

    @Test
    fun `a refresh for a row the user already left leaves the cursor alone`() {
        val state = grid(focusedIndex = 4).copy(currentRow = HomeRow.Favorites)

        assertNull(state.indexAfterRefresh(HomeRow.Continue, listOf(1L, 2L, 3L), anchorGameId = null))
    }
}
