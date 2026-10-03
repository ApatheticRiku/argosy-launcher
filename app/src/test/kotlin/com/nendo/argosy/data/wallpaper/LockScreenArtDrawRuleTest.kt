package com.nendo.argosy.data.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenArtDrawRuleTest {

    @Test
    fun `a play session with no art from its launch leaves the lock screen alone`() {
        assertFalse(LockScreenArtManager.mayDraw(gameId = 9981L, shownKey = null, isLaunch = false))
    }

    @Test
    fun `a play session showing another game's art leaves the lock screen alone`() {
        assertFalse(LockScreenArtManager.mayDraw(gameId = 9981L, shownKey = "game:13468:/art.jpg:1280x960", isLaunch = false))
    }

    @Test
    fun `a play session showing its own game's art may keep it`() {
        assertTrue(LockScreenArtManager.mayDraw(gameId = 9981L, shownKey = "game:9981:/art.jpg:1280x960", isLaunch = false))
    }

    @Test
    fun `a launch may always draw`() {
        assertTrue(LockScreenArtManager.mayDraw(gameId = 9981L, shownKey = null, isLaunch = true))
    }

    @Test
    fun `outside a play session the library art may draw`() {
        assertTrue(LockScreenArtManager.mayDraw(gameId = null, shownKey = null, isLaunch = false))
    }
}
