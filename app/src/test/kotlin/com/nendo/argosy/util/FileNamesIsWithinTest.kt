package com.nendo.argosy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileNamesIsWithinTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `an entry inside the target is within it`() {
        val target = tempDir.newFolder("Zelda")
        assertTrue(FileNames.isWithin(File(target, "saves/slot1.srm"), target))
        assertTrue(FileNames.isWithin(target, target))
    }

    @Test
    fun `a sibling sharing the target's name prefix is outside it`() {
        val target = tempDir.newFolder("Zelda")
        assertFalse(FileNames.isWithin(File(target, "../Zelda2/x.srm"), target))
    }

    @Test
    fun `climbing out of the target is outside it`() {
        val target = tempDir.newFolder("Zelda")
        assertFalse(FileNames.isWithin(File(target, "../../etc/x"), target))
    }

    @Test
    fun `an asset name cannot leave its folder once sanitized`() {
        val cache = tempDir.newFolder("emulator_apks")
        assertTrue(FileNames.isWithin(File(cache, FileNames.sanitize("../../files/x.apk")), cache))
        assertTrue(FileNames.isWithin(File(cache, FileNames.sanitize("..")), cache))
    }
}
