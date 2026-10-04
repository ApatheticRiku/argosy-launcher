package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration202To203Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_202_203.migrate(db)
        return captured
    }

    @Test
    fun `art is copied before the old games table is dropped`() {
        val sql = statements()
        val createArt = sql.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `game_art`") }
        val copies = sql.withIndex().filter { it.value.startsWith("INSERT INTO `game_art`") }.map { it.index }
        val drop = sql.indexOfFirst { it == "DROP TABLE `games`" }

        assertTrue(createArt >= 0)
        assertEquals(3, copies.size)
        assertTrue(copies.all { it in (createArt + 1) until drop })
    }

    @Test
    fun `each slot copies its own columns`() {
        val copies = statements().filter { it.startsWith("INSERT INTO `game_art`") }

        assertTrue(copies.single { "'COVER'" in it }.let { "`coverPath`" in it && "`coverOverridePath`" in it && "`gradientColors`" in it })
        assertTrue(copies.single { "'BACKGROUND'" in it }.let { "`backgroundPath`" in it && "`backgroundOverridePath`" in it })
        assertTrue(copies.single { "'LOGO'" in it }.let { "`logoPath`" in it && "`logoOverridePath`" in it })
    }

    @Test
    fun `a local path becomes the cached path and anything else the source url`() {
        val cover = statements().single { it.startsWith("INSERT INTO `game_art`") && "'COVER'" in it }

        assertTrue("CASE WHEN substr(NULLIF(`coverPath`, ''), 1, 1) = '/' THEN NULL ELSE NULLIF(`coverPath`, '') END" in cover)
        assertTrue("CASE WHEN substr(NULLIF(`coverPath`, ''), 1, 1) = '/' THEN NULLIF(`coverPath`, '') ELSE NULL END" in cover)
    }

    @Test
    fun `the rebuilt games table carries no art column`() {
        val create = statements().single { it.contains("CREATE TABLE IF NOT EXISTS `games_new`") }
        val removed = listOf(
            "`coverPath`", "`backgroundPath`", "`logoPath`", "`coverOverridePath`",
            "`backgroundOverridePath`", "`logoOverridePath`", "`gradientColors`", "`coverAspectRatio`"
        )

        removed.forEach { assertFalse(it, it in create) }
        assertTrue("`cachedScreenshotPaths` TEXT" in create)
        assertTrue("`boxSpinePath` TEXT" in create)
    }

    @Test
    fun `every games index is recreated`() {
        val indices = statements().filter { it.startsWith("CREATE") && "INDEX" in it && "ON `games`" in it }
            .map { it.substringAfter("`index_games_").substringBefore("`") }
            .toSet()

        assertEquals(
            setOf(
                "platformId", "title", "lastPlayed", "source", "rommId", "steamAppId", "packageName",
                "regions", "gameModes", "franchises", "genres", "collections", "siblingGroupKey"
            ),
            indices
        )
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_202_203, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(203)
    }
}
