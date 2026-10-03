package com.nendo.argosy.data.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class FileAccessLayerSaveAccessTest {

    private val context = mockk<Context>(relaxed = true)
    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val managedStorageAccessor = mockk<ManagedStorageAccessor>(relaxed = true)
    private val rootFileAccessor = mockk<RootFileAccessor>(relaxed = true)
    private lateinit var fal: FileAccessLayerImpl

    private val restricted = "/storage/emulated/0/Android/data/com.github.stenzek.duckstation/files/memcards/Game_1.mcd"
    private val open = "/storage/emulated/0/RetroArch/saves/Game.srm"

    @Before
    fun setUp() {
        every { androidDataAccessor.isRestrictedAndroidPath(any()) } answers { firstArg<String>().contains("/Android/data/") }
        every { androidDataAccessor.syncMirror(any()) } returns null
        fal = FileAccessLayerImpl(context, androidDataAccessor, managedStorageAccessor, rootFileAccessor)
    }

    @Test
    fun `a mirrored device syncs the copy instead of granting group access`() {
        every { rootFileAccessor.isAvailable } returns true
        every { androidDataAccessor.syncMirror(listOf(restricted)) } returns true

        fal.prepareSaveAccess(restricted, open, null)

        verify(exactly = 1) { androidDataAccessor.syncMirror(listOf(restricted)) }
        verify(exactly = 0) { rootFileAccessor.grantGroupAccess(any()) }
    }

    @Test
    fun `commit reports a failed copy out and passes everywhere nothing is mirrored`() {
        every { androidDataAccessor.syncMirror(listOf(restricted)) } returns false
        assert(!fal.commitSaveAccess(restricted, open))

        every { androidDataAccessor.syncMirror(listOf(restricted)) } returns null
        assert(fal.commitSaveAccess(restricted, open))
        assert(fal.commitSaveAccess(open, null))
    }

    @Test
    fun `grants only the restricted paths in one call`() {
        every { rootFileAccessor.isAvailable } returns true

        fal.prepareSaveAccess(restricted, open, null)

        verify(exactly = 1) { rootFileAccessor.grantGroupAccess(listOf(restricted)) }
    }

    @Test
    fun `skips the root call when no path is restricted`() {
        every { rootFileAccessor.isAvailable } returns true

        fal.prepareSaveAccess(open, null)

        verify(exactly = 0) { rootFileAccessor.grantGroupAccess(any()) }
    }

    @Test
    fun `does nothing on a device without the root daemon`() {
        every { rootFileAccessor.isAvailable } returns false

        fal.prepareSaveAccess(restricted)

        verify(exactly = 0) { rootFileAccessor.grantGroupAccess(any()) }
    }

    @Test
    fun `lowers emulated and sdcard paths to data media and refuses anything outside a package`() {
        assert(RootFileAccessor.lowerPath(restricted) == "/data/media/0/Android/data/com.github.stenzek.duckstation/files/memcards/Game_1.mcd")
        assert(RootFileAccessor.lowerPath("/sdcard/Android/data/pkg/files/a.sav") == "/data/media/0/Android/data/pkg/files/a.sav")
        assert(RootFileAccessor.lowerPath("/storage/emulated/0/⁦Android/data/pkg/files/a.sav") == "/data/media/0/Android/data/pkg/files/a.sav")
        assert(RootFileAccessor.lowerPath("/storage/emulated/0/Android/data/pkg") == null)
        assert(RootFileAccessor.lowerPath("/storage/emulated/0/Android/data/pkg/../../x") == null)
        assert(RootFileAccessor.lowerPath(open) == null)
    }
}
