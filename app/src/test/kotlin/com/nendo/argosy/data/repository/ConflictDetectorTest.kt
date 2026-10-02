package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMSave
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ConflictDetectorTest {
    private val detector = ConflictDetector()

    private val newerServerSave = RomMSave(
        id = 9L,
        romId = 1L,
        userId = 1L,
        emulator = "mgba",
        fileName = "save.srm",
        updatedAt = "2026-10-02T00:00:00Z"
    )

    @Test
    fun `a device-fenced server decides conflicts itself, so the client never guesses from clocks`() {
        val decision = detector.detectUploadConflict(
            gameId = 1L,
            channelName = null,
            forceOverwrite = false,
            currentDeviceId = "device-1",
            latestServerSave = newerServerSave,
            localModified = Instant.parse("2026-01-01T00:00:00Z")
        )

        assertNull(decision)
    }

    @Test
    fun `a server without device sync keeps the timestamp guard`() {
        val decision = detector.detectUploadConflict(
            gameId = 1L,
            channelName = null,
            forceOverwrite = false,
            currentDeviceId = null,
            latestServerSave = newerServerSave,
            localModified = Instant.parse("2026-01-01T00:00:00Z")
        )

        assertTrue(decision?.isConflict == true)
    }
}
