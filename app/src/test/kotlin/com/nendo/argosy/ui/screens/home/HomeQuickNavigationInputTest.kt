package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuickNavigationInputTest {

    private fun handler(quickNavigation: Boolean, state: HomeUiState = HomeUiState()): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        every { actions.quickNavigation() } returns quickNavigation
        return HomeInputHandler(actions, isDefaultView = true, onGameSelect = {}, onNavigateToDefault = {}, onDrawerToggle = {}) to actions
    }

    @Test
    fun `quick navigation moves rows with the triggers and leaves the bumpers to the nav bar`() {
        val (handler, actions) = handler(quickNavigation = true)

        assertTrue(handler.onNextTrigger().handled)
        assertFalse(handler.onNextSection().handled)

        verify(exactly = 1) { actions.nextRow() }
    }

    @Test
    fun `without quick navigation the bumpers move rows and the triggers do nothing`() {
        val (handler, actions) = handler(quickNavigation = false)

        assertTrue(handler.onPrevSection().handled)
        assertFalse(handler.onNextTrigger().handled)

        verify(exactly = 1) { actions.previousRow() }
        verify(exactly = 0) { actions.nextRow() }
    }

    @Test
    fun `without quick navigation the tile picker keeps bumpers for tabs and triggers for letters`() {
        val (handler, actions) = handler(
            quickNavigation = false,
            state = HomeUiState(customGrid = com.nendo.argosy.ui.components.CustomGridState(showPicker = true))
        )

        handler.onNextSection()
        handler.onNextTrigger()

        verify(exactly = 1) { actions.cycleTilePickerCategory(1) }
        verify(exactly = 1) { actions.jumpTilePickerLetter(true) }
        verify(exactly = 0) { actions.nextRow() }
    }
}
