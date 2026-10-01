package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.util.AppPaths
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class StateCacheManagerScreenshotTest {

    private val filesDir = createTempDirectory("state_screenshot").toFile()
    private val context = mockk<Context>(relaxed = true).also { every { it.filesDir } returns filesDir }

    private val manager = StateCacheManager(
        context = context,
        gameDao = mockk(relaxed = true),
        stateCacheDao = mockk(relaxed = true),
        stateTombstoneDao = mockk(relaxed = true),
        saveCacheDao = mockk(relaxed = true),
        saveSyncDao = mockk(relaxed = true),
        pendingSyncQueueDao = mockk(relaxed = true),
        emulatorSaveConfigDao = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        coreVersionExtractor = mockk(relaxed = true),
        retroArchConfigParser = mockk(relaxed = true),
        retroArchPathResolver = mockk(relaxed = true),
        libretroStatePathResolver = mockk(relaxed = true),
        saveSyncApiClient = mockk(relaxed = true),
        payloadCodec = mockk(relaxed = true),
        attributionRepository = mockk(relaxed = true),
        stateOwnershipTracker = mockk(relaxed = true)
    )

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `a screenshot that cannot be written does not fail the state restore`() {
        val cached = File(AppPaths.stateCacheDir(filesDir), "1/shot.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val liveState = File(filesDir, "states/game.state").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9))
        }
        File("${liveState.absolutePath}.png", "occupied").apply { parentFile?.mkdirs(); writeText("x") }

        manager.restoreScreenshot(entity(screenshotPath = "1/${cached.name}"), liveState)

        assertTrue(liveState.exists())
    }

    private fun entity(screenshotPath: String) = StateCacheEntity(
        gameId = 1L,
        platformSlug = "snes",
        emulatorId = "argosy",
        slotNumber = 1,
        cachedAt = Instant.now(),
        stateSize = 1,
        cachePath = "1/game.state",
        screenshotPath = screenshotPath
    )
}
