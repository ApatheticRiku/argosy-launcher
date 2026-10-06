package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.sigil.SigilFileEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class FalSigilFileAccessTest {

    private val fal = mockk<FileAccessLayer>()
    private val access = FalSigilFileAccess(fal)

    @Test
    fun `a folder that is not there lists as null`() {
        every { fal.isDirectory("/saves/memcards") } returns false

        assertNull(access.list("/saves", "memcards"))
    }

    @Test(expected = IOException::class)
    fun `a folder that exists but cannot be listed throws`() {
        every { fal.isDirectory("/saves/memcards") } returns true
        every { fal.listFilesUnion("/saves/memcards") } returns emptyList()
        every { fal.listFiles("/saves/memcards") } returns null

        access.list("/saves", "memcards")
    }

    @Test
    fun `an empty readable folder lists as empty`() {
        every { fal.isDirectory("/saves/memcards") } returns true
        every { fal.listFilesUnion("/saves/memcards") } returns emptyList()
        every { fal.listFiles("/saves/memcards") } returns emptyList()

        assertEquals(emptyList<SigilFileEntry>(), access.list("/saves", "memcards"))
    }

    @Test
    fun `a folder with entries lists them`() {
        val card = FileInfo("/saves/memcards/Mcd001.ps2", "Mcd001.ps2", isDirectory = false, isFile = true, size = 8L, lastModified = 0L)
        every { fal.isDirectory("/saves/memcards") } returns true
        every { fal.listFilesUnion("/saves/memcards") } returns listOf(card)

        assertEquals(listOf(SigilFileEntry("Mcd001.ps2", false)), access.list("/saves", "memcards"))
    }
}
