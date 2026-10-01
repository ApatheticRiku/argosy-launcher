package com.nendo.argosy.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class MissingArtRegistryTest {

    private val dir = createTempDirectory("missing_art").toFile()
    private val file = dir.resolve("missing_art.tsv")
    private var now = 1_000_000L
    private val week = 7L * 24 * 60 * 60 * 1000

    private fun registry() = MissingArtRegistry(file, clock = { now })

    @Test
    fun `a url the server reported missing is skipped until the retry window passes`() {
        val url = "https://romm.example/assets/romm/resources/roms/6/13264/box2d_back/box2d_back.png"
        val registry = registry()
        assertFalse(registry.isKnownMissing(url))

        registry.markMissing(url)
        assertTrue(registry.isKnownMissing(url))

        now += week - 1
        assertTrue(registry.isKnownMissing(url))
        now += 1
        assertFalse(registry.isKnownMissing(url))
    }

    @Test
    fun `marks survive a new registry reading the same file`() {
        val url = "https://romm.example/assets/romm/resources/roms/6/1/box2d_side/box2d_side.png"
        registry().markMissing(url)

        assertTrue(registry().isKnownMissing(url))
        assertFalse(registry().isKnownMissing("https://romm.example/other.png"))
    }

    @Test
    fun `expired marks are dropped from the file on the next write`() {
        registry().markMissing("https://romm.example/old.png")
        now += week
        registry().markMissing("https://romm.example/new.png")

        val lines = file.readLines()
        assertTrue(lines.size == 1 && lines.single().endsWith("new.png"))
    }
}
