package com.nendo.argosy.ui.screens.settings.delegates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelListTest {

    private val levels = VolumeLevels.AMBIENT_AUDIO

    @Test
    fun `a stored value above the loudest level resolves to the loudest level`() {
        assertEquals(35, levelIn(50, levels))
    }

    @Test
    fun `a stored value below the quietest level resolves to the quietest level`() {
        assertEquals(2, levelIn(0, levels))
    }

    @Test
    fun `a value between levels resolves to the next level up`() {
        assertEquals(20, levelIn(12, levels))
    }

    @Test
    fun `stepping down from an out-of-range value starts from the loudest level`() {
        assertEquals(20, adjustInList(50, levels, -1))
    }

    @Test
    fun `stepping up from an out-of-range value lands on the loudest level`() {
        assertEquals(35, adjustInList(50, levels, 1))
    }

    @Test
    fun `stepping up from the loudest level does nothing`() {
        assertNull(adjustInList(35, levels, 1))
    }
}
