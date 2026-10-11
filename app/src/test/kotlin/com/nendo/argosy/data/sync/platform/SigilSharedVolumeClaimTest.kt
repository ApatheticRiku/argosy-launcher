package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun row(state: ByteArray, unowned: String, updatedAt: Long = 1L) =
        SigilSyncStateEntity(3L, "scd", "genesis_plus_gx", "/saves", state, unowned, updatedAt)

    @Test
    fun `a collect keeps the committed state and records the unowned names`() {
        val stored = row(byteArrayOf(1), "SONICCD___")
        val collected = row(byteArrayOf(2), "SONICCD___\nLUNAR_SSS_")

        val written = SigilSaveHandler.collectRow(stored, collected, claimed = false)

        assertArrayEquals(byteArrayOf(1), written.state)
        assertEquals("SONICCD___\nLUNAR_SSS_", written.unowned)
    }

    @Test
    fun `the first collect of a volume stores no state but its unowned names`() {
        val written = SigilSaveHandler.collectRow(null, row(byteArrayOf(2), "SONICCD___"), claimed = false)

        assertNull(SigilSaveHandler.committedState(written))
        assertEquals("SONICCD___", written.unowned)
        assertEquals(emptyList<String>(), SigilSaveHandler.newlyUnowned(written.unowned, listOf("SONICCD___")))
    }

    @Test
    fun `a collect that claimed saves stores its state at once`() {
        val written = SigilSaveHandler.collectRow(row(byteArrayOf(1), "LUNAR_SSS_"), row(byteArrayOf(2), ""), claimed = true)

        assertArrayEquals(byteArrayOf(2), written.state)
    }

    @Test
    fun `a commit stores the collected state beside the newest unowned names`() {
        val stored = row(byteArrayOf(1), "LATER_SAVE")

        val written = SigilSaveHandler.commitRow(stored, row(byteArrayOf(2), "OLDER_SAVE"), now = 9L)

        assertArrayEquals(byteArrayOf(2), written.state)
        assertEquals("LATER_SAVE", written.unowned)
        assertEquals(9L, written.updatedAt)
    }
}
