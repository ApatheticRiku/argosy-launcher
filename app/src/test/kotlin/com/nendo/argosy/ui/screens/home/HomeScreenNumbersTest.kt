package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.components.AppLaunchTarget
import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.screens.home.delegates.GameMenuState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenNumbersTest {

    private val twoScreens = listOf(AppLaunchTarget(0, 1), AppLaunchTarget(4, 2))

    @Test
    fun `an open game menu with two screens shows the numbers`() {
        val state = HomeUiState(gameMenuDisplays = twoScreens)

        assertTrue(showsScreenNumbers(state, GameMenuState(showGameMenu = true)))
    }

    @Test
    fun `a game menu closed by a modal reset hides the numbers even with displays still recorded`() {
        val state = HomeUiState(gameMenuDisplays = twoScreens)

        assertFalse(showsScreenNumbers(state, GameMenuState(showGameMenu = false)))
    }

    @Test
    fun `a single screen never shows the numbers`() {
        val state = HomeUiState(gameMenuDisplays = twoScreens.take(1))

        assertFalse(showsScreenNumbers(state, GameMenuState(showGameMenu = true)))
    }

    @Test
    fun `an app bar menu offering two screens shows the numbers until it closes`() {
        val menu = AppBarLaunchMenu(
            packageName = "pkg",
            label = "App",
            rows = listOf(
                AppMenuRow.OpenOnScreen(0, 1, "a"),
                AppMenuRow.OpenOnScreen(4, 2, "b")
            )
        )

        assertTrue(showsScreenNumbers(HomeUiState(appBarMenu = menu), GameMenuState()))
        assertFalse(showsScreenNumbers(HomeUiState(appBarMenu = null), GameMenuState()))
    }
}
