package com.nendo.argosy.libretro

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveWrittenDetectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var saves: File

    @Before
    fun setup() {
        saves = tempFolder.newFolder("saves")
    }

    private fun manager(primarySavePath: String? = null) = SaveStateManager(
        savesDir = saves,
        statesDir = tempFolder.newFolder("states"),
        romPath = File(tempFolder.root, "roms/Crash.cue").absolutePath,
        gameId = 1L,
        activeSaveRepository = mockk(relaxed = true),
        saveCacheManager = mockk(relaxed = true),
        primarySavePath = primarySavePath
    )

    private fun File.writeSave(fill: (Int) -> Byte) {
        parentFile?.mkdirs()
        writeBytes(ByteArray(64) { fill(it) })
        setLastModified(lastModified() + 2_000)
    }

    @Test
    fun `nothing written since the baseline reports nothing`() {
        val mgr = manager()
        mgr.seedWrittenSaves()

        assertFalse(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `a memory card the core writes is reported`() {
        val mgr = manager()
        mgr.seedWrittenSaves()

        File(saves, "Crash.0.mcr").writeSave { (it % 7).toByte() }

        assertTrue(mgr.takeNewlyWrittenSaves())
        assertFalse(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `blank save RAM is not reported`() {
        val mgr = manager()
        mgr.seedWrittenSaves()

        File(saves, "Crash.srm").writeSave { 0xFF.toByte() }

        assertFalse(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `a clock file changing is not reported`() {
        val mgr = manager()
        mgr.seedWrittenSaves()

        File(saves, "Crash.rtc").writeSave { it.toByte() }

        assertFalse(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `a core-owned save outside the saves folder is reported`() {
        val vmu = File(tempFolder.root, "flycast/vmu_save_A1.bin")
        val mgr = manager(primarySavePath = vmu.absolutePath)
        mgr.seedWrittenSaves()

        vmu.writeSave { (it % 5).toByte() }

        assertTrue(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `a card already present at the baseline is not reported`() {
        File(saves, "Crash.0.mcr").writeSave { (it % 7).toByte() }
        val mgr = manager()

        assertFalse(mgr.takeNewlyWrittenSaves())
        assertFalse(mgr.takeNewlyWrittenSaves())
    }

    @Test
    fun `another game's save is not reported`() {
        val mgr = manager()
        mgr.seedWrittenSaves()

        File(saves, "Spyro.0.mcr").writeSave { (it % 7).toByte() }

        assertFalse(mgr.takeNewlyWrittenSaves())
    }
}
