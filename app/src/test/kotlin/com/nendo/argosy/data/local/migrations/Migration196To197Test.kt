package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration196To197Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_196_197.migrate(db)
    }

    @Test
    fun `every existing tile becomes a paged tile`() {
        assertEquals(
            "ALTER TABLE `home_tiles` ADD COLUMN `gridKind` TEXT NOT NULL DEFAULT 'PAGED'",
            statements.first()
        )
    }

    @Test
    fun `the kind is indexed beside the owner and page the tile queries filter on`() {
        val index = statements.single { it.startsWith("CREATE INDEX") }

        assertTrue("`index_home_tiles_ownerUserId_gridKind_pageIndex`" in index)
        assertTrue("ON `home_tiles` (`ownerUserId`, `gridKind`, `pageIndex`)" in index)
    }

    @Test
    fun `nothing else is touched`() {
        assertEquals(2, statements.size)
        assertTrue(statements.all { "`home_tiles`" in it })
    }

    @Test
    fun `the migration is registered as the last step`() {
        assertEquals(Migration_196_197, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(197)
    }
}
