package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration201To202Test {

    @Test
    fun `adds a nullable prefer-newer-server-save column to the platform overrides`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }

        Migration_201_202.migrate(db)

        assertEquals(1, statements.size)
        val sql = statements.single()
        assert("platform_libretro_settings" in sql && "preferNewerServerSave" in sql && "NOT NULL" !in sql) { sql }
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_201_202, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(202)
    }
}
