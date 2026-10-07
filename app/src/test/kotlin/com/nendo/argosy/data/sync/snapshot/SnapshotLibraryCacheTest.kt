package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotLibraryCacheTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val moshi = Moshi.Builder().build()

    private fun cache(): SnapshotLibraryCache {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir.root
        return SnapshotLibraryCache(context, moshi)
    }

    private val library = SnapshotLibrary(
        romFileId = 99,
        mine = listOf(SnapshotChannelEntry(RomMChannel(id = "c-main", label = "Main Game", currentSnapshotId = 50, romFileId = 99), emptyList())),
        community = emptyList(),
        backups = emptyList(),
        deviceChannelId = "c-main"
    )

    @Test
    fun `a stored library comes back after a restart`() = runBlocking {
        cache().put(3L, 7L, library)

        assertEquals(library, cache().get(3L, 7L))
    }

    @Test
    fun `another account never reads it`() = runBlocking {
        cache().put(3L, 7L, library)

        assertNull(cache().get(4L, 7L))
    }
}
