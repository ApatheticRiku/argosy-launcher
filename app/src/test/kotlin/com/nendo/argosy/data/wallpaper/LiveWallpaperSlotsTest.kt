package com.nendo.argosy.data.wallpaper

import android.app.WallpaperManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWallpaperSlotsTest {

    private val home = WallpaperManager.FLAG_SYSTEM
    private val lock = WallpaperManager.FLAG_LOCK

    @Test
    fun `ours on home with the user's own lock wallpaper clears home only`() {
        assertEquals(home, liveWallpaperSlots(homeIsOurs = true, lockIsOurs = false))
    }

    @Test
    fun `ours on the lock screen only never touches the user's home wallpaper`() {
        assertEquals(lock, liveWallpaperSlots(homeIsOurs = false, lockIsOurs = true))
    }

    @Test
    fun `a lock screen with no wallpaper of its own follows home`() {
        assertEquals(home or lock, liveWallpaperSlots(homeIsOurs = true, lockIsOurs = null))
        assertEquals(0, liveWallpaperSlots(homeIsOurs = false, lockIsOurs = null))
    }

    @Test
    fun `ours on both slots clears both`() {
        assertEquals(home or lock, liveWallpaperSlots(homeIsOurs = true, lockIsOurs = true))
    }
}
