package com.nendo.argosy.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataAccessorVolumeTest {

    @Test
    fun `internal shared storage needs alt access under every alias`() {
        assertTrue(AndroidDataAccessor.isOnInternalVolume("/storage/emulated/0/Android/data/com.armsx2/files"))
        assertTrue(AndroidDataAccessor.isOnInternalVolume("/storage/self/primary/Android/data/com.armsx2/files"))
        assertTrue(AndroidDataAccessor.isOnInternalVolume("/sdcard/Android/data/com.armsx2/files"))
    }

    @Test
    fun `an sd card keeps its plain Android data path`() {
        assertFalse(AndroidDataAccessor.isOnInternalVolume("/storage/2C32-CEEB/Android/data/com.armsx2/files/memcards"))
    }
}
