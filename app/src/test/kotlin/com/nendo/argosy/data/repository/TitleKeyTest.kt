package com.nendo.argosy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleKeyTest {

    @Test
    fun `region and revision tags do not split a title`() {
        assertEquals(
            titleKey("Sonic 3 & Knuckles"),
            titleKey("Sonic 3 & Knuckles (USA) [Rev 1]")
        )
    }

    @Test
    fun `case and punctuation do not split a title`() {
        assertEquals(titleKey("Pokémon - Blue Version"), titleKey("pokemon blue version"))
    }

    @Test
    fun `different games stay apart`() {
        assertNotEquals(titleKey("Pokemon Blue Version"), titleKey("Pokemon WaterBlue Version"))
    }
}
