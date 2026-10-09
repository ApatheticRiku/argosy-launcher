package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration206To207Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_206_207.migrate(db)
        return captured
    }

    @Test
    fun `adds a held-by-choice flag to snapshot channels, off for every existing row`() {
        assertEquals(
            listOf("ALTER TABLE `snapshot_channels` ADD COLUMN `heldByChoice` INTEGER NOT NULL DEFAULT 0"),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertTrue(Migration_206_207 in MigrationRegistry.ALL)
    }
}
