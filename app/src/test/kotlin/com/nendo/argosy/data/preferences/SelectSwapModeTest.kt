package com.nendo.argosy.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectSwapModeTest {

    @Test
    fun `a device that never chose a gesture holds select to swap`() {
        assertEquals(SelectSwapMode.HOLD, SelectSwapMode.fromString(null))
        assertEquals(SelectSwapMode.HOLD, ControlsPreferences().selectSwapMode)
        assertEquals(SelectSwapMode.HOLD, UserPreferences().selectSwapMode)
    }

    @Test
    fun `an unknown stored token falls back to hold`() {
        assertEquals(SelectSwapMode.HOLD, SelectSwapMode.fromString("SWAP_ON_PRESS"))
    }

    @Test
    fun `each stored token reads back as its own mode`() {
        SelectSwapMode.entries.forEach {
            assertEquals(it, SelectSwapMode.fromString(it.name))
        }
    }
}
