package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MAX
import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MIN
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationSectionTest {

    @Test
    fun `the shade and art rows step and stop at both ends`() {
        val first = PresentationStyle(scrim = PresentationScrim.GRADIENT, art = PresentationArt.COVER)
        val last = PresentationStyle(scrim = PresentationScrim.NONE, art = PresentationArt.TITLE)

        assertEquals(first, adjustPresentationItem(first, PresentationItem.Scrim, -1))
        assertEquals(first, adjustPresentationItem(first, PresentationItem.Art, -1))
        assertEquals(last, adjustPresentationItem(last, PresentationItem.Scrim, 1))
        assertEquals(last, adjustPresentationItem(last, PresentationItem.Art, 1))
        assertEquals(PresentationScrim.SOLID, adjustPresentationItem(first, PresentationItem.Scrim, 1)?.scrim)
        assertEquals(PresentationArt.BOX_3D, adjustPresentationItem(first, PresentationItem.Art, 1)?.art)
    }

    @Test
    fun `strength steps and stops at both ends`() {
        val atTop = PresentationStyle(scrimStrength = PRESENTATION_SCRIM_MAX)
        val atBottom = PresentationStyle(scrimStrength = PRESENTATION_SCRIM_MIN)

        assertEquals(
            PRESENTATION_SCRIM_MAX,
            adjustPresentationItem(atTop, PresentationItem.ScrimStrength, 1)?.scrimStrength
        )
        assertEquals(
            PRESENTATION_SCRIM_MIN,
            adjustPresentationItem(atBottom, PresentationItem.ScrimStrength, -1)?.scrimStrength
        )
        assertEquals(
            PRESENTATION_SCRIM_MAX - 10,
            adjustPresentationItem(atTop, PresentationItem.ScrimStrength, -1)?.scrimStrength
        )
    }

    @Test
    fun `left turns a stat off and right turns it on`() {
        val item = PresentationItem.Stat(PresentationStat.PLAY_TIME)
        val off = PresentationStyle(hiddenStats = setOf(PresentationStat.PLAY_TIME))

        val on = adjustPresentationItem(off, item, 1)
        assertTrue(on!!.shows(PresentationStat.PLAY_TIME))
        assertFalse(adjustPresentationItem(on, item, -1)!!.shows(PresentationStat.PLAY_TIME))
        assertTrue(adjustPresentationItem(on, item, 1)!!.shows(PresentationStat.PLAY_TIME))
    }

    @Test
    fun `headers take no adjustment`() {
        val header = PresentationItem.ALL.first { it is PresentationItem.Header }

        assertNull(adjustPresentationItem(PresentationStyle(), header, 1))
    }

    @Test
    fun `strength is hidden while there is no shade`() {
        val none = PresentationStyle(scrim = PresentationScrim.NONE)
        val solid = PresentationStyle(scrim = PresentationScrim.SOLID)

        assertFalse(PresentationItem.ScrimStrength.visibleWhen(none))
        assertTrue(PresentationItem.ScrimStrength.visibleWhen(solid))
        assertEquals(presentationMaxFocusIndex(solid) - 1, presentationMaxFocusIndex(none))
    }

    @Test
    fun `the layout row steps and stops at both ends`() {
        val first = PresentationStyle(layout = PresentationLayout.CINEMATIC)
        val last = PresentationStyle(layout = PresentationLayout.LOGO)

        assertEquals(PresentationLayout.CINEMATIC, adjustPresentationItem(first, PresentationItem.Layout, -1)?.layout)
        assertEquals(PresentationLayout.JOURNAL, adjustPresentationItem(first, PresentationItem.Layout, 1)?.layout)
        assertEquals(PresentationLayout.LOGO, adjustPresentationItem(last, PresentationItem.Layout, 1)?.layout)
    }

    @Test
    fun `each layout shows only the rows it draws`() {
        val cinematic = presentationSectionItems(PresentationLayout.CINEMATIC)
        val journal = presentationSectionItems(PresentationLayout.JOURNAL)
        val logo = presentationSectionItems(PresentationLayout.LOGO)

        assertTrue(PresentationItem.Art in cinematic)
        assertTrue(cinematic.any { it is PresentationItem.Stat })

        assertFalse(PresentationItem.Art in journal)
        assertTrue(journal.any { it is PresentationItem.Stat })

        assertFalse(PresentationItem.Art in logo)
        assertFalse(logo.any { it is PresentationItem.Stat })
        assertTrue(PresentationItem.Layout in logo)
        assertTrue(PresentationItem.Scrim in logo)
    }

    @Test
    fun `a section left with no rows loses its header`() {
        val logoKeys = presentationSectionItems(PresentationLayout.LOGO).map { it.key }

        assertFalse("artworkHeader" in logoKeys)
        assertFalse("statsHeader" in logoKeys)
        assertTrue("backgroundHeader" in logoKeys)
    }

    private fun presentationSectionItems(layout: PresentationLayout): List<PresentationItem> =
        presentationVisibleItems(PresentationStyle(layout = layout))
}
