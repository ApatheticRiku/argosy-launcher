package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration199To200Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_199_200.migrate(db)
    }

    private fun indexOfFirst(fragment: String) = statements.indexOfFirst { fragment in it }

    private fun columnsOf(create: String): List<String> =
        create.substringAfter("(").substringBefore("FOREIGN KEY")
            .split(", ")
            .map { it.trim() }
            .filter { it.startsWith("`") }
            .map { it.substringAfter("`").substringBefore("`") }

    @Test
    fun `the rebuilt table drops the main sibling column and keeps the sibling group columns`() {
        val create = statements.single { it.startsWith("CREATE TABLE IF NOT EXISTS `games_new`") }

        assertFalse("rommMainSibling" in create)
        assertTrue("`siblingGroupKey` TEXT" in create)
        assertTrue("`isHackVariant` INTEGER NOT NULL DEFAULT 0" in create)
        assertTrue("`isTranslationVariant` INTEGER NOT NULL DEFAULT 0" in create)
        assertTrue("`isGroupVisible` INTEGER NOT NULL DEFAULT 1" in create)
        assertTrue("FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" in create)
    }

    @Test
    fun `every column of the rebuilt table is copied from the old one`() {
        val create = statements.single { it.startsWith("CREATE TABLE IF NOT EXISTS `games_new`") }
        val copy = statements.single { it.startsWith("INSERT INTO `games_new`") }
        val target = copy.substringAfter("(").substringBefore(")").split(", ").map { it.trim('`') }
        val source = copy.substringAfter("SELECT ").substringBefore(" FROM").split(", ").map { it.trim('`') }

        assertEquals(columnsOf(create), target)
        assertEquals(target, source)
        assertTrue(copy.endsWith("FROM `games`"))
    }

    @Test
    fun `the data moves before the old table is dropped`() {
        val copy = indexOfFirst("INSERT INTO `games_new`")
        val drop = indexOfFirst("DROP TABLE `games`")
        val rename = indexOfFirst("ALTER TABLE `games_new` RENAME TO `games`")

        assertTrue(copy >= 0)
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
                "packageName", "regions", "gameModes", "franchises", "genres", "collections",
                "siblingGroupKey"
            ),
            indexed
        )
    }

    @Test
    fun `the overlay table is left alone`() {
        assertTrue(statements.none { "game_user_overlay" in it })
    }

    @Test
    fun `the migration is registered`() {
        assertEquals(Migration_199_200, MigrationRegistry.byKey(199, 200))
    }
}
