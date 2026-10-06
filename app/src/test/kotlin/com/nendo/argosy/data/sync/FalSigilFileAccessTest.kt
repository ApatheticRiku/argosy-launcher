package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayerImpl
import com.nendo.argosy.data.storage.ManagedStorageAccessor
import com.nendo.argosy.data.storage.RootFileAccessor
import com.nendo.sigil.SigilFileEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class FalSigilFileAccessTest {

    private val androidData = mockk<AndroidDataAccessor>(relaxed = true)
    private val managed = mockk<ManagedStorageAccessor>(relaxed = true)
    private val root = mockk<RootFileAccessor>(relaxed = true)
    private val fal = FileAccessLayerImpl(mockk<Context>(relaxed = true), androidData, managed, root)
    private val access = FalSigilFileAccess(fal)

    private val saveRoot = "/storage/emulated/0/Android/data/xyz.aethersx2.android/files"
    private val relativeCards = "Android/data/xyz.aethersx2.android/files/memcards"

    @Before
    fun setUp() {
        every { androidData.isRestrictedAndroidPath(any()) } answers { firstArg<String>().contains("/Android/data/") }
        every { androidData.isAltAccessSupported() } returns false
        every { root.isAvailable } returns false
    }

    private fun managedFolder(entries: List<ManagedStorageAccessor.DocumentFile>?, listable: Boolean) {
        every { managed.isDirectoryAtPath("primary", relativeCards) } returns true
        every { managed.listFiles("primary", relativeCards) } returns entries
        every { managed.isListableDirectory("primary", relativeCards) } returns listable
    }

    @Test
    fun `a folder that is not there lists as null`() {
        every { managed.isDirectoryAtPath(any(), any()) } returns false

        assertNull(access.list(saveRoot, "memcards"))
    }

    @Test(expected = IOException::class)
    fun `a folder the device refuses to list throws`() {
        managedFolder(entries = null, listable = false)

        access.list(saveRoot, "memcards")
    }

    @Test
    fun `an empty android data folder lists as empty`() {
        managedFolder(entries = null, listable = true)

        assertEquals(emptyList<SigilFileEntry>(), access.list(saveRoot, "memcards"))
    }

    @Test
    fun `an empty folder the alt tier reaches lists as empty`() {
        every { androidData.isAltAccessSupported() } returns true
        every { androidData.isDirectory("$saveRoot/memcards") } returns true
        every { androidData.listFiles("$saveRoot/memcards") } returns emptyArray()
        managedFolder(entries = null, listable = false)

        assertEquals(emptyList<SigilFileEntry>(), access.list(saveRoot, "memcards"))
    }

    @Test
    fun `a folder with entries lists them`() {
        val card = ManagedStorageAccessor.DocumentFile(
            documentId = "primary:$relativeCards/Mcd001.ps2",
            displayName = "Mcd001.ps2",
            mimeType = "application/octet-stream",
            lastModified = 0L,
            size = 8L,
            flags = 0,
            isDirectory = false
        )
        managedFolder(entries = listOf(card), listable = true)

        assertEquals(listOf(SigilFileEntry("Mcd001.ps2", false)), access.list(saveRoot, "memcards"))
    }
}
