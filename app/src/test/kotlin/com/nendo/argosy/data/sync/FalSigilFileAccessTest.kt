package com.nendo.argosy.data.sync

import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.sigil.SigilFileEntry
import io.mockk.every
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FalSigilFileAccessTest {

    private val root = createTempDirectory("sigil-fal").toFile()
    private val fal = realFsFal()
    private val access = FalSigilFileAccess(fal)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `listing names files and folders relative to the root`() {
        File(root, "memcards/Mcd001.ps2").mkdirs()
        File(root, "memcards/Mcd002.ps2").writeBytes(byteArrayOf(1))

        val entries = access.list(root.path, "memcards")!!.sortedBy { it.name }

        assertEquals(
            listOf(SigilFileEntry("Mcd001.ps2", true), SigilFileEntry("Mcd002.ps2", false)),
            entries
        )
    }

    @Test
    fun `an absent folder lists as null, not as empty`() {
        assertNull(access.list(root.path, "missing"))
    }

    @Test
    fun `the empty path names the root itself`() {
        File(root, "a.srm").writeBytes(byteArrayOf(1))

        assertEquals(listOf(SigilFileEntry("a.srm", false)), access.list(root.path, ""))
    }

    @Test
    fun `listing goes through the union so restricted folders list every tier`() {
        File(root, "card").mkdirs()

        access.list(root.path, "card")

        verify { fal.listFilesUnion("${root.path}/card") }
    }

    @Test
    fun `a write makes the folders above it and a read returns the bytes`() {
        assertTrue(access.write(root.path, "a/b/save.gci", byteArrayOf(4, 5)))

        assertArrayEquals(byteArrayOf(4, 5), access.read(root.path, "a/b/save.gci"))
    }

    @Test
    fun `a write that the access layer refuses reports failure`() {
        every { fal.writeBytes(any(), any()) } returns false

        assertFalse(access.write(root.path, "save.srm", byteArrayOf(1)))
    }

    @Test
    fun `a trailing slash removes a folder, otherwise a file`() {
        File(root, "BASLUS-20312/icon.sys").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        File(root, "save.srm").writeBytes(byteArrayOf(1))

        assertTrue(access.remove(root.path, "BASLUS-20312/"))
        assertTrue(access.remove(root.path, "save.srm"))

        assertFalse(File(root, "BASLUS-20312").exists())
        assertFalse(File(root, "save.srm").exists())
    }
}
