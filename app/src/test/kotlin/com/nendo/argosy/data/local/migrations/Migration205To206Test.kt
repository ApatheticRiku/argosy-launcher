package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration205To206Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_205_206.migrate(db)
        return captured
    }

    @Test
    fun `adds a nullable save format to the save cache and touches nothing else`() {
        assertEquals(listOf("ALTER TABLE `save_cache` ADD COLUMN `saveFormat` TEXT"), statements())
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_205_206, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(206)
    }
}
