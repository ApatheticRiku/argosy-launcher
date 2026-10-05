package com.nendo.argosy.data.emulator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GameCubeHeaderParserRegionTest {

    private val dir = createTempDirectory("gci-region").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun gci(gameId: String): File = File(dir, "$gameId.gci").apply {
        val bytes = ByteArray(0x40)
        "${gameId}01".toByteArray().copyInto(bytes, 0)
        "save".toByteArray().copyInto(bytes, 0x08)
        writeBytes(bytes)
    }

    private fun regionOf(gameId: String): String? = GameCubeHeaderParser.parseGciHeader(gci(gameId))?.region

    @Test
    fun `a Korean game id saves in the folder Dolphin reads, JAP`() {
        assertEquals("JAP", regionOf("GZLK"))
    }

    @Test
    fun `each retail region maps to its Dolphin folder`() {
        assertEquals("USA", regionOf("GZLE"))
        assertEquals("EUR", regionOf("GZLP"))
        assertEquals("JAP", regionOf("GZLJ"))
    }

    @Test
    fun `a KOR folder is not a GCI location`() {
        assertFalse(GameCubeHeaderParser.isValidGciPath("/saves/GC/KOR/Card A/01-GZLK-save.gci"))
    }
}
