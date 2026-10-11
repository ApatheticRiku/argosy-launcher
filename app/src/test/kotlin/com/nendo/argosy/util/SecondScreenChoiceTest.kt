package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecondScreenChoiceTest {

    private val main = screen(displayId = 0, width = 1920, height = 1080, builtIn = true)
    private val lower = screen(displayId = 4, width = 1240, height = 1080, builtIn = true)
    private val monitor = screen(displayId = 7, width = 3840, height = 2160, builtIn = false)
    private val smallMonitor = screen(displayId = 8, width = 800, height = 480, builtIn = false)

    @Test
    fun `a game on the main panel puts its second screen on the other built-in panel`() {
        assertEquals(lower, ScreenCatalog.chooseSecondScreen(listOf(main, lower), gameDisplayId = 0))
    }

    @Test
    fun `a game on the lower panel puts its second screen on the main panel`() {
        assertEquals(main, ScreenCatalog.chooseSecondScreen(listOf(main, lower), gameDisplayId = 4))
    }

    @Test
    fun `a built-in panel wins over an external monitor, even a smaller monitor`() {
        assertEquals(
            lower,
            ScreenCatalog.chooseSecondScreen(listOf(main, lower, smallMonitor), gameDisplayId = 0)
        )
    }

    @Test
    fun `with the game on a monitor the smaller built-in panel takes the second screen`() {
        assertEquals(lower, ScreenCatalog.chooseSecondScreen(listOf(main, lower, monitor), gameDisplayId = 7))
    }

    @Test
    fun `an external monitor takes the second screen when it is the only other screen`() {
        assertEquals(monitor, ScreenCatalog.chooseSecondScreen(listOf(main, monitor), gameDisplayId = 0))
    }

    @Test
    fun `a single screen has nowhere to put a second screen`() {
        assertNull(ScreenCatalog.chooseSecondScreen(listOf(main), gameDisplayId = 0))
    }

    private fun screen(displayId: Int, width: Int, height: Int, builtIn: Boolean) = AttachedScreen(
        key = "display:$displayId",
        displayId = displayId,
        number = displayId + 1,
        widthPx = width,
        heightPx = height,
        builtIn = builtIn
    )
}
