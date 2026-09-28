package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration195To196Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_195_196.migrate(db)
    }

    private fun indexOfFirst(fragment: String) = statements.indexOfFirst { fragment in it }

    @Test
    fun `the rebuilt table drops the manual-cover columns and adds the three overrides`() {
        val create = statements.single { it.startsWith("CREATE TABLE IF NOT EXISTS `games_new`") }

        assertFalse("coverSetManually" in create)
        assertFalse("originalCoverPath" in create)
        assertTrue("`coverOverridePath` TEXT" in create)
        assertTrue("`backgroundOverridePath` TEXT" in create)
        assertTrue("`logoOverridePath` TEXT" in create)
    }

    @Test
    fun `a manual cover moves to the override and the original becomes the server cover`() {
        val copy = statements.single { it.startsWith("INSERT INTO `games_new`") }

        assertTrue(
            "CASE WHEN `coverSetManually` = 1 THEN `originalCoverPath` ELSE `coverPath` END" in copy
        )
        assertTrue("CASE WHEN `coverSetManually` = 1 THEN `coverPath` ELSE NULL END" in copy)
    }

    @Test
    fun `a screenshot background moves to the background override`() {
        val copy = statements.single { it.startsWith("INSERT INTO `games_new`") }

        assertTrue(
            "CASE WHEN `backgroundPath` LIKE '%/bg_custom_%' THEN `backgroundPath` ELSE NULL END" in copy
        )
        assertTrue(
            "CASE WHEN `backgroundPath` LIKE '%/bg_custom_%' THEN NULL ELSE `backgroundPath` END" in copy
        )
    }

    @Test
    fun `the data moves before the old table is dropped`() {
        val pages = indexOfFirst("UPDATE `home_grid_pages`")
        val copy = indexOfFirst("INSERT INTO `games_new`")
        val drop = indexOfFirst("DROP TABLE `games`")
        val rename = indexOfFirst("ALTER TABLE `games_new` RENAME TO `games`")

        assertTrue(pages in 0 until copy)
        assertTrue(copy < drop)
        assertTrue(drop < rename)
    }

    @Test
    fun `every games index is recreated after the rename`() {
        val rename = indexOfFirst("ALTER TABLE `games_new` RENAME TO `games`")
        val indexed = statements.drop(rename + 1)
            .filter { "INDEX IF NOT EXISTS `index_games_" in it }
            .map { it.substringAfter("`index_games_").substringBefore("`") }
            .toSet()

        assertEquals(
            setOf(
                "platformId", "title", "lastPlayed", "source", "rommId", "steamAppId",
                "packageName", "regions", "gameModes", "franchises", "genres", "collections"
            ),
            indexed
        )
    }

    @Test
    fun `the migration is registered`() {
        assertEquals(Migration_195_196, MigrationRegistry.byKey(195, 196))
    }
}
