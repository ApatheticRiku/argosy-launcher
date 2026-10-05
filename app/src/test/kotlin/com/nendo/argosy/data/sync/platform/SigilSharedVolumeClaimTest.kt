package com.nendo.argosy.data.sync.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class SigilSharedVolumeClaimTest {

    @Test
    fun `the first collect of a volume claims nothing`() {
        assertEquals(emptyList<String>(), SigilSaveHandler.newlyUnowned(null, listOf("SONICCD___", "LUNAR_SSS_")))
    }

    @Test
    fun `saves that appeared since the previous collect are claimed`() {
        assertEquals(listOf("LUNAR_SSS_"), SigilSaveHandler.newlyUnowned("SONICCD___", listOf("SONICCD___", "LUNAR_SSS_")))
    }

    @Test
    fun `saves already unowned at the previous collect stay unowned`() {
        assertEquals(emptyList<String>(), SigilSaveHandler.newlyUnowned("SONICCD___\nLUNAR_SSS_", listOf("LUNAR_SSS_")))
    }

    @Test
    fun `an empty previous listing makes every unowned save new`() {
        assertEquals(listOf("SONICCD___"), SigilSaveHandler.newlyUnowned("", listOf("SONICCD___")))
    }
}
