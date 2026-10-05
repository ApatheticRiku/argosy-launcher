package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration203To204Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_203_204.migrate(db)
        return captured
    }

    @Test
    fun `creates the sigil state table keyed by account, platform, layout and root`() {
        val create = statements().single { it.contains("CREATE TABLE IF NOT EXISTS `sigil_sync_state`") }
        assertTrue(create.contains("PRIMARY KEY(`ownerUserId`, `platformSlug`, `layout`, `root`)"))
    }

    @Test
    fun `game boy carts lose their stored features so they rescan`() {
        val reset = statements().single { it.startsWith("UPDATE `games` SET `saveFeatures` = NULL") }
        listOf("'gb'", "'gbc'", "'gameboy'", "'game-boy-color'").forEach { assertTrue(it, reset.contains(it)) }
        assertTrue(!reset.contains("'gba'"))
    }
}
