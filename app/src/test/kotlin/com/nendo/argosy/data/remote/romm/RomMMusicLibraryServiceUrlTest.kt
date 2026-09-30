package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomMMusicLibraryServiceUrlTest {

    @Test
    fun `resource paths are encoded per segment`() {
        assertEquals(
            "/assets/romm/resources/roms/1/Final%20Fantasy%20%26%20More/cover%23big.png",
            RomMMusicLibraryService.encodeResourcePath(
                "/assets/romm/resources/roms/1/Final Fantasy & More/cover#big.png"
            )
        )
    }

    @Test
    fun `a query string survives encoding untouched`() {
        assertEquals(
            "/assets/romm/resources/roms/2/big%20cover.png?ts=2024-01-01 10:00:00",
            RomMMusicLibraryService.encodeResourcePath(
                "/assets/romm/resources/roms/2/big cover.png?ts=2024-01-01 10:00:00"
            )
        )
    }

    @Test
    fun `bearer auth is limited to the server origin`() {
        val base = "https://romm.example.com"
        assertTrue(RomMMusicLibraryService.sameOrigin("https://romm.example.com/api/roms/1/files/content/a.ogg", base))
        assertTrue(RomMMusicLibraryService.sameOrigin("https://ROMM.example.com:443/api/x", "$base/"))
        assertFalse(RomMMusicLibraryService.sameOrigin("https://cdn.example.com/api/x", base))
        assertFalse(RomMMusicLibraryService.sameOrigin("http://romm.example.com/api/x", base))
        assertFalse(RomMMusicLibraryService.sameOrigin("https://romm.example.com:8443/api/x", base))
        assertFalse(RomMMusicLibraryService.sameOrigin("https://romm.example.com/api/x", ""))
    }
}
