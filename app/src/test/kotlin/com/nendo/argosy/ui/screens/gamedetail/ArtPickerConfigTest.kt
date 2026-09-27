package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.model.ArtSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtPickerConfigTest {

    @Test
    fun `covers show three portrait tiles cropped to box art shape`() {
        val config = ArtSlot.COVER.pickerConfig

        assertEquals(3, config.columns)
        assertEquals(2f / 3f, config.tileAspectRatio, 0.0001f)
        assertTrue(config.cropsToTile)
        assertFalse(config.checkeredBackdrop)
        assertFalse(config.offersScreenshots)
    }

    @Test
    fun `backgrounds show two wide tiles and offer the game's screenshots`() {
        val config = ArtSlot.BACKGROUND.pickerConfig

        assertEquals(2, config.columns)
        assertTrue(config.tileAspectRatio > 1f)
        assertFalse(config.cropsToTile)
        assertFalse(config.checkeredBackdrop)
        assertTrue(config.offersScreenshots)
    }

    @Test
    fun `logos show three tiles on a checkered backdrop without cropping`() {
        val config = ArtSlot.LOGO.pickerConfig

        assertEquals(3, config.columns)
        assertFalse(config.cropsToTile)
        assertTrue(config.checkeredBackdrop)
        assertFalse(config.offersScreenshots)
    }

    @Test
    fun `cover search needs server cover search support`() {
        assertTrue(ArtSlot.COVER.pickerConfig.canSearch(serverSupportsCoverSearch = true))
        assertFalse(ArtSlot.COVER.pickerConfig.canSearch(serverSupportsCoverSearch = false))
    }

    @Test
    fun `background and logo search is always attempted`() {
        assertTrue(ArtSlot.BACKGROUND.pickerConfig.canSearch(serverSupportsCoverSearch = false))
        assertTrue(ArtSlot.LOGO.pickerConfig.canSearch(serverSupportsCoverSearch = false))
    }

    @Test
    fun `stepping within a row wraps across the whole grid`() {
        assertEquals(0, artGridStep(index = 6, delta = 1, size = 7, columns = 3))
        assertEquals(6, artGridStep(index = 0, delta = -1, size = 7, columns = 3))
        assertEquals(2, artGridStep(index = 1, delta = 1, size = 7, columns = 3))
    }

    @Test
    fun `stepping a row inside the grid moves straight down or up`() {
        assertEquals(4, artGridStep(index = 1, delta = 3, size = 7, columns = 3))
        assertEquals(1, artGridStep(index = 4, delta = -3, size = 7, columns = 3))
    }

    @Test
    fun `stepping down off the last row wraps to the same column on top`() {
        assertEquals(2, artGridStep(index = 5, delta = 3, size = 7, columns = 3))
        assertEquals(0, artGridStep(index = 6, delta = 3, size = 7, columns = 3))
    }

    @Test
    fun `stepping up off the top row lands on the same column at the bottom`() {
        assertEquals(6, artGridStep(index = 0, delta = -3, size = 7, columns = 3))
        assertEquals(4, artGridStep(index = 1, delta = -3, size = 7, columns = 3))
        assertEquals(3, artGridStep(index = 1, delta = -2, size = 4, columns = 2))
    }

    @Test
    fun `an empty grid keeps focus at zero`() {
        assertEquals(0, artGridStep(index = 0, delta = 1, size = 0, columns = 3))
    }
}
