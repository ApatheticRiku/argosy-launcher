package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration204To205Test {

    private val addFlag = "ALTER TABLE `pending_conflicts` ADD COLUMN `isHardcoreDowngrade` INTEGER NOT NULL DEFAULT 0"

    private fun statements(pendingConflictColumns: List<String>): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        val cursor = mockk<Cursor>(relaxed = true)
        var row = -1
        every { cursor.getColumnIndex("name") } returns 1
        every { cursor.moveToNext() } answers { row += 1; row < pendingConflictColumns.size }
        every { cursor.getString(1) } answers { pendingConflictColumns[row] }
        every { db.query("PRAGMA table_info(`pending_conflicts`)") } returns cursor
        Migration_204_205.migrate(db)
        return captured
    }

    @Test
    fun `a database that already has the hardcore flag is not altered again`() {
        val run = statements(listOf("id", "gameId", "isHardcoreDowngrade", "ownerUserId"))

        assertTrue(run.none { it == addFlag })
    }

    @Test
    fun `a database without the hardcore flag gains it`() {
        val run = statements(listOf("id", "gameId", "ownerUserId"))

        assertEquals(1, run.count { it == addFlag })
    }

    @Test
    fun `the snapshot channel table is rebuilt either way`() {
        listOf(listOf("id"), listOf("id", "isHardcoreDowngrade")).forEach { columns ->
            val run = statements(columns)
            assertTrue(run.contains("DROP TABLE IF EXISTS `snapshot_channels`"))
            val create = run.single { it.contains("CREATE TABLE IF NOT EXISTS `snapshot_channels`") }
            assertTrue(create.contains("PRIMARY KEY(`ownerUserId`, `gameId`, `label`)"))
        }
    }
}
