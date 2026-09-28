package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryQuickMenuRowsTest {

    private fun game(isDownloaded: Boolean = false) = LibraryGameUi(
        id = 1,
        title = "Game",
        sortTitle = "game",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        coverPath = null,
        source = GameSource.ROMM_REMOTE,
        isFavorite = false,
        isDownloaded = isDownloaded,
        isRommGame = true,
        isAndroidApp = false,
        emulatorName = null
    )

    @Test
    fun `the active variant row is hidden for a single-member group`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = false, hasSiblingGroup = false)

        assertFalse(rows.contains(LibraryQuickMenuRow.ACTIVE_VARIANT))
    }

    @Test
    fun `the active variant row follows the collection rows and precedes refresh`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = true, hasSiblingGroup = true)

        assertEquals(
            listOf(
                LibraryQuickMenuRow.PRIMARY,
                LibraryQuickMenuRow.FAVORITE,
                LibraryQuickMenuRow.DETAILS,
                LibraryQuickMenuRow.ADD_TO_COLLECTION,
                LibraryQuickMenuRow.ADD_TO_GRID,
                LibraryQuickMenuRow.ACTIVE_VARIANT,
                LibraryQuickMenuRow.REFRESH,
                LibraryQuickMenuRow.RESYNC_PLATFORM,
                LibraryQuickMenuRow.HIDE
            ),
            rows
        )
    }

    @Test
    fun `the row list keeps the order the menu had before the active variant row`() {
        val rows = libraryQuickMenuRows(game(isDownloaded = true), isCustomGridHome = false, hasSiblingGroup = false)

        assertEquals(
            listOf(
                LibraryQuickMenuRow.PRIMARY,
                LibraryQuickMenuRow.FAVORITE,
                LibraryQuickMenuRow.DETAILS,
                LibraryQuickMenuRow.ADD_TO_COLLECTION,
                LibraryQuickMenuRow.REFRESH,
                LibraryQuickMenuRow.RESYNC_PLATFORM,
                LibraryQuickMenuRow.DELETE,
                LibraryQuickMenuRow.HIDE
            ),
            rows
        )
    }
}
