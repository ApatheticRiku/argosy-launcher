package com.nendo.argosy.data.sync.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotChannelsTest {

    @Test
    fun `the Argosy default channel in every spelling maps to the default label`() {
        assertEquals("default", SnapshotChannels.labelOf(null))
        assertEquals("default", SnapshotChannels.labelOf("autosave"))
        assertEquals("default", SnapshotChannels.labelOf("Default"))
        assertEquals("Speedrun", SnapshotChannels.labelOf("Speedrun"))
    }

    @Test
    fun `the default label maps back to the Argosy default channel in any case`() {
        assertNull(SnapshotChannels.argosyChannelOf("default"))
        assertNull(SnapshotChannels.argosyChannelOf("DEFAULT"))
        assertEquals("Speedrun", SnapshotChannels.argosyChannelOf("Speedrun"))
    }

    @Test
    fun `a label survives the round trip through its Argosy channel`() {
        listOf("default", "Speedrun", "100%").forEach { label ->
            assertEquals(label, SnapshotChannels.labelOf(SnapshotChannels.argosyChannelOf(label)))
        }
    }

    @Test
    fun `names of the default channel are reserved`() {
        assertTrue(SnapshotChannels.isReservedLabel("default"))
        assertTrue(SnapshotChannels.isReservedLabel(" Default "))
        assertTrue(SnapshotChannels.isReservedLabel("autosave"))
        assertFalse(SnapshotChannels.isReservedLabel("Defaulted"))
    }
}
