package com.nendo.argosy.ui.screens.gamedetail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSpreadTest {

    @Test
    fun `the cover stands alone and later pages pair even with odd`() {
        assertEquals(0..0, spreadOf(0, 10))
        assertEquals(1..2, spreadOf(1, 10))
        assertEquals(1..2, spreadOf(2, 10))
        assertEquals(3..4, spreadOf(4, 10))
    }

    @Test
    fun `a last page with no partner stands alone`() {
        assertEquals(9..9, spreadOf(9, 10))
        assertEquals(1..1, spreadOf(1, 2))
    }

    @Test
    fun `turning walks the book one spread at a time`() {
        assertEquals(1, spreadStartAfter(0, 10, 1))
        assertEquals(3, spreadStartAfter(1, 10, 1))
        assertEquals(3, spreadStartAfter(2, 10, 1))
        assertEquals(1, spreadStartAfter(3, 10, -1))
        assertEquals(0, spreadStartAfter(1, 10, -1))
    }

    @Test
    fun `turning stops at both covers`() {
        assertEquals(0, spreadStartAfter(0, 10, -1))
        assertEquals(9, spreadStartAfter(9, 10, 1))
    }

    @Test
    fun `a flat scan of landscape spreads is not paired`() {
        val astroFang = List(12) { 842 to 252 }

        assertTrue(isFlatSpreadScan(astroFang))
    }

    @Test
    fun `portrait and landscape single pages still pair`() {
        assertFalse(isFlatSpreadScan(List(12) { 600 to 900 }))
        assertFalse(isFlatSpreadScan(List(12) { 900 to 600 }))
        assertFalse(isFlatSpreadScan(List(12) { 1000 to 500 }))
    }

    @Test
    fun `one wide fold-out among single pages does not unpair the book`() {
        val pages = List(11) { 600 to 900 } + (1800 to 600)

        assertFalse(isFlatSpreadScan(pages))
    }

    @Test
    fun `a spread scan with a single-page cover is still unpaired`() {
        val pages = listOf(420 to 250) + List(11) { 842 to 252 }

        assertTrue(isFlatSpreadScan(pages))
    }
}
