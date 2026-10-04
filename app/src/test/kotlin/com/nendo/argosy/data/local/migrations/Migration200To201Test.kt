package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Migration200To201Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_200_201.migrate(db)
    }

    @Test
    fun `duplicates are folded before the survivors are renamed`() {
        val delete = statements.indexOfFirst { it.startsWith("DELETE FROM `save_sync`") }
        val rename = statements.indexOfFirst { it.startsWith("UPDATE `save_sync` SET `channelName` = 'autosave'") }

        assertTrue(delete >= 0)
        assertTrue(delete < rename)
    }

    @Test
    fun `every spelling of the latest slot is folded`() {
        statements.forEach { sql ->
            assertTrue(sql, "IS NULL" in sql && "'autosave'" in sql && "'argosy-latest'" in sql)
        }
    }

    @Test
    fun `the survivor prefers a restore point, then the latest sync, then the newest row`() {
        val delete = statements.single { it.startsWith("DELETE FROM `save_sync`") }

        assertTrue(
            "ORDER BY k.`userSelectedRestorePoint` DESC, IFNULL(k.`lastSyncedAt`, 0) DESC, k.`id` DESC" in delete
        )
    }

    @Test
    fun `the migration is registered in sequence`() {
        assertEquals(Migration_200_201, MigrationRegistry.byKey(200, 201))
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
