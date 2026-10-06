package com.nendo.argosy.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentHueTest {

    @Test
    fun `the default accent starts the wheel at its middle`() {
        assertEquals(180f, AccentHue.hueOf(null), 0.5f)
    }

    @Test
    fun `a colour round-trips through its hue`() {
        assertEquals(120f, AccentHue.hueOf(AccentHue.colorAt(120f)), 0.5f)
    }

    @Test
    fun `shifting past the end of the wheel wraps around`() {
        assertEquals(5f, AccentHue.hueOf(AccentHue.shifted(AccentHue.colorAt(355f), AccentHue.STEP)), 0.5f)
        assertEquals(355f, AccentHue.hueOf(AccentHue.shifted(AccentHue.colorAt(5f), -AccentHue.STEP)), 0.5f)
    }
}
