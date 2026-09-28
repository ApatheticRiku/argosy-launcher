package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration197To198Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_197_198.migrate(db)
    }

    @Test
    fun `the sibling columns are added with their entity defaults`() {
        val alters = statements.filter { it.startsWith("ALTER TABLE `games`") }

        assertEquals(
            listOf(
                "ALTER TABLE `games` ADD COLUMN `siblingGroupKey` TEXT",
                "ALTER TABLE `games` ADD COLUMN `isHackVariant` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `games` ADD COLUMN `isTranslationVariant` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `games` ADD COLUMN `rommMainSibling` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `games` ADD COLUMN `isGroupVisible` INTEGER NOT NULL DEFAULT 1"
            ),
            alters
        )
    }

    @Test
    fun `the group key is indexed`() {
        assertTrue(
            "CREATE INDEX IF NOT EXISTS `index_games_siblingGroupKey` ON `games` (`siblingGroupKey`)" in statements
        )
    }

    @Test
    fun `the backfill follows RomM's gallery order and touches only server rows on a server platform`() {
        val backfill = statements.single { it.startsWith("UPDATE `games` SET `siblingGroupKey`") }
        val order = listOf("igdbId", "ssId", "mobyId", "raId", "hasheousId", "launchboxId", "tgdbId", "flashpointId")
        val positions = order.map { backfill.indexOf("WHEN `$it` IS NOT NULL") }

        assertTrue("every id is consulted: $positions", positions.none { it < 0 })
        assertEquals(positions.sorted(), positions)
        assertTrue("'igdb-' || `platformId` || '-' || `igdbId`" in backfill)
        assertTrue(backfill.endsWith("WHERE `rommId` > 0 AND `platformId` > 0"))
    }

    @Test
    fun `the picks table matches its entity`() {
        val create = statements.single { it.startsWith("CREATE TABLE IF NOT EXISTS `game_group_picks`") }

        assertTrue("`ownerUserId` INTEGER, " in create)
        assertTrue("`groupKey` TEXT NOT NULL" in create)
        assertTrue("`gameId` INTEGER NOT NULL" in create)
        assertTrue("REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" in create)
        assertTrue(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_group_picks_ownerUserId_groupKey` " +
                "ON `game_group_picks` (`ownerUserId`, `groupKey`)" in statements
        )
        assertTrue(
            "CREATE INDEX IF NOT EXISTS `index_game_group_picks_gameId` ON `game_group_picks` (`gameId`)" in statements
        )
    }

    @Test
    fun `the migration is registered`() {
        assertEquals(Migration_197_198, MigrationRegistry.byKey(197, 198))
    }
}
