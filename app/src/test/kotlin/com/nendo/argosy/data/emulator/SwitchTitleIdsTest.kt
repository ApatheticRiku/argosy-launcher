package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchTitleIdsTest {

    private val bdspBase = "0100000011D90000"
    private val arceusBase = "01001F5010DFA000"

    @Test
    fun `an application id is its own base`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId(bdspBase))
        assertEquals(arceusBase, SwitchTitleIds.baseApplicationId(arceusBase))
    }

    @Test
    fun `a patch id resolves to its application`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId("0100000011D90800"))
        assertEquals(arceusBase, SwitchTitleIds.baseApplicationId("01001F5010DFA800"))
    }

    @Test
    fun `an add-on content id resolves to its application`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId("0100000011D91001"))
        assertEquals(arceusBase, SwitchTitleIds.baseApplicationId("01001F5010DFB001"))
    }

    @Test
    fun `a high add-on content index still resolves to its application`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId("0100000011D917D0"))
    }

    @Test
    fun `the add-on content base id resolves to its application`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId("0100000011D91000"))
    }

    @Test
    fun `a lowercase patch id resolves to the uppercase application`() {
        assertEquals(bdspBase, SwitchTitleIds.baseApplicationId("0100000011d90800"))
    }

    @Test
    fun `a lowercase application id comes back unchanged`() {
        assertEquals("0100000011d90000", SwitchTitleIds.baseApplicationId("0100000011d90000"))
    }

    @Test
    fun `an id outside the application range comes back unchanged`() {
        assertEquals("010000000000100D", SwitchTitleIds.baseApplicationId("010000000000100D"))
        assertEquals("FF00000011D90800", SwitchTitleIds.baseApplicationId("FF00000011D90800"))
    }

    @Test
    fun `a value that is not a 16 hex id comes back unchanged`() {
        assertEquals("", SwitchTitleIds.baseApplicationId(""))
        assertEquals("0100000011D908", SwitchTitleIds.baseApplicationId("0100000011D908"))
        assertEquals("0100000011D9080G", SwitchTitleIds.baseApplicationId("0100000011D9080G"))
    }

    @Test
    fun `an application id at the bottom of the range pads to 16 digits`() {
        assertEquals("0100000000010000", SwitchTitleIds.baseApplicationId("0100000000010800"))
    }
}
