package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.SelectSwapMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleSwapInputTest {

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    @Test
    fun `select swaps screens on a dual screen device with a presentation screen and no game`() {
        install(dualScreen = true, presentation = true, gameActive = false)

        assertTrue(selectSwapsRoles())
    }

    @Test
    fun `select keeps the screen's action while a game runs`() {
        install(dualScreen = true, presentation = true, gameActive = true)

        assertFalse(selectSwapsRoles())
    }

    @Test
    fun `select swaps screens while a game runs when the game can move with the swap`() {
        install(dualScreen = true, presentation = true, gameActive = true, liveSwap = true)

        assertTrue(selectSwapsRoles())
    }

    @Test
    fun `live swap alone does not make select swap without a presentation screen`() {
        install(dualScreen = true, presentation = false, gameActive = true, liveSwap = true)

        assertFalse(selectSwapsRoles())
    }

    @Test
    fun `select keeps the screen's action without a presentation screen`() {
        install(dualScreen = true, presentation = false, gameActive = false)

        assertFalse(selectSwapsRoles())
    }

    @Test
    fun `select keeps the screen's action on a single screen device`() {
        install(dualScreen = false, presentation = true, gameActive = false)

        assertFalse(selectSwapsRoles())
    }

    @Test
    fun `select keeps the screen's action before the manager exists`() {
        assertFalse(selectSwapsRoles())
        assertFalse(selectHoldSwapsRoles())
        assertNull(activeSelectSwapMode())
    }

    @Test
    fun `hold mode keeps a press of select for the screen and gives the hold to the swap`() {
        install(dualScreen = true, presentation = true, gameActive = false, mode = SelectSwapMode.HOLD)

        assertFalse(selectSwapsRoles())
        assertTrue(selectHoldSwapsRoles())
        assertEquals(SelectSwapMode.HOLD, activeSelectSwapMode())
    }

    @Test
    fun `tap mode gives the press to the swap and leaves the hold unbound`() {
        install(dualScreen = true, presentation = true, gameActive = false, mode = SelectSwapMode.TAP)

        assertTrue(selectSwapsRoles())
        assertFalse(selectHoldSwapsRoles())
        assertEquals(SelectSwapMode.TAP, activeSelectSwapMode())
    }

    @Test
    fun `hold mode does not swap while a game that cannot move runs`() {
        install(dualScreen = true, presentation = true, gameActive = true, mode = SelectSwapMode.HOLD)

        assertFalse(selectHoldSwapsRoles())
        assertNull(activeSelectSwapMode())
    }

    @Test
    fun `hold mode swaps while a game runs when the game can move with the swap`() {
        install(
            dualScreen = true,
            presentation = true,
            gameActive = true,
            liveSwap = true,
            mode = SelectSwapMode.HOLD
        )

        assertTrue(selectHoldSwapsRoles())
    }

    @Test
    fun `hold mode has no swap on a single screen device`() {
        install(dualScreen = false, presentation = true, gameActive = false, mode = SelectSwapMode.HOLD)

        assertFalse(selectHoldSwapsRoles())
        assertNull(activeSelectSwapMode())
    }

    private fun install(
        dualScreen: Boolean,
        presentation: Boolean,
        gameActive: Boolean,
        liveSwap: Boolean = false,
        mode: SelectSwapMode = SelectSwapMode.TAP
    ) {
        DualScreenManagerHolder.instance = mockk<DualScreenManager> {
            every { isDualScreenDevice } returns MutableStateFlow(dualScreen)
            every { hasPresentationScreen } returns MutableStateFlow(presentation)
            every { swappedIsGameActive } returns MutableStateFlow(gameActive)
            every { liveSwapAvailable } returns MutableStateFlow(liveSwap)
            every { selectSwapMode } returns MutableStateFlow(mode)
        }
    }
}
