package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration198To199Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_198_199.migrate(db)
    }

    @Test
    fun `the overlay gains the main sibling column with its entity default`() {
        assertEquals(
            "ALTER TABLE `game_user_overlay` ADD COLUMN `rommMainSibling` INTEGER NOT NULL DEFAULT 0",
            statements.first()
        )
    }

    @Test
    fun `the shared column is carried over only when a single account holds the overlay`() {
        val backfill = statements.single { it.startsWith("UPDATE `game_user_overlay`") }

        assertTrue("SET `rommMainSibling` = 1" in backfill)
        assertTrue("SELECT `id` FROM `games` WHERE `rommMainSibling` = 1" in backfill)
        assertTrue(backfill.endsWith("(SELECT COUNT(DISTINCT `ownerUserId`) FROM `game_user_overlay`) = 1"))
    }

    @Test
    fun `the migration leaves the games table alone`() {
        assertTrue(statements.none { it.startsWith("ALTER TABLE `games`") || it.startsWith("UPDATE `games`") })
    }

    @Test
    fun `the migration is registered as the last step`() {
        assertEquals(Migration_198_199, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(199)
    }
}
