package com.nendo.argosy.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutBoxArtTest {

    @Test
    fun `a carousel that drew boxes keeps 3d box art`() {
        val legacy = """{"selected":"CAROUSEL","carousel":{"useBoxArt":true},"autoGrid":{"useBoxArt":false}}"""

        assertTrue(HomeLayoutSettings.fromJson(legacy).boxArt3d)
    }

    @Test
    fun `an auto grid that drew boxes keeps 3d box art`() {
        val legacy = """{"selected":"AUTO_GRID","carousel":{"useBoxArt":false},"autoGrid":{"useBoxArt":true}}"""

        assertTrue(HomeLayoutSettings.fromJson(legacy).boxArt3d)
    }

    @Test
    fun `layouts that never drew boxes start on 2d covers`() {
        val legacy = """{"selected":"CAROUSEL","carousel":{},"autoGrid":{}}"""

        assertFalse(HomeLayoutSettings.fromJson(legacy).boxArt3d)
    }

    @Test
    fun `the stored choice wins over the legacy switches`() {
        val stored = """{"selected":"CAROUSEL","boxArt3d":false,"carousel":{"useBoxArt":true}}"""

        assertFalse(HomeLayoutSettings.fromJson(stored).boxArt3d)
    }

    @Test
    fun `the choice survives a round trip`() {
        val settings = HomeLayoutSettings(boxArt3d = true)

        assertTrue(HomeLayoutSettings.fromJson(settings.toJson()).boxArt3d)
        assertFalse(HomeLayoutSettings.fromJson(HomeLayoutSettings().toJson()).boxArt3d)
    }
}
