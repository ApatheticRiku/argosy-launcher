package com.nendo.argosy.ui.components.gamelist

import com.nendo.argosy.domain.model.SaveListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameListRowTest {

    @Test
    fun `synced saves draw the floppy muted`() {
        assertEquals(SaveMarkTone.MUTED, SaveListState.SYNCED.markTone)
    }

    @Test
    fun `saves ahead on either side draw the floppy in the accent`() {
        assertEquals(SaveMarkTone.ACCENT, SaveListState.LOCAL_AHEAD.markTone)
        assertEquals(SaveMarkTone.ACCENT, SaveListState.SERVER_AHEAD.markTone)
    }

    @Test
    fun `a conflict or hardcore resolution draws the floppy in the error color`() {
        assertEquals(SaveMarkTone.ERROR, SaveListState.NEEDS_ATTENTION.markTone)
    }

    @Test
    fun `the rating stays in the footer when the tabs leave room for it`() {
        assertTrue(ratingFitsFooter(tabsWidth = 100, ratingWidth = 40, gap = 8, available = 200))
    }

    @Test
    fun `the rating stays in the footer when it fills the room exactly`() {
        assertTrue(ratingFitsFooter(tabsWidth = 152, ratingWidth = 40, gap = 8, available = 200))
    }

    @Test
    fun `the rating leaves the footer when the tabs crowd it`() {
        assertFalse(ratingFitsFooter(tabsWidth = 153, ratingWidth = 40, gap = 8, available = 200))
    }
}
