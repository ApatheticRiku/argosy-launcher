package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VariantFileCleanupTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val gameFileDao = mockk<GameFileDao>(relaxed = true)
    private val appPreferences = mockk<AppPreferencesRepository>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true)
    private lateinit var cleanup: VariantFileCleanup
    private var done = false

    @Before
    fun setup() {
        done = false
        coEvery { appPreferences.isVariantFileCleanupDone() } answers { done }
        coEvery { appPreferences.setVariantFileCleanupDone() } answers { done = true }
        coEvery { gameFileDao.hasVersionGroupedFiles() } returns false
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false
        coEvery { gameDao.getGamesWithFileSelection() } returns emptyList()
        cleanup = VariantFileCleanup(gameDao, gameFileDao, appPreferences, syncPreferences)
    }

    private fun game(id: Long, activeVariantFileId: Long? = null, lastPlayedFileId: Long? = null) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "snes",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = "/roms/snes/Game $id/Game $id.sfc",
        rommId = 100L + id,
        igdbId = null,
        source = GameSource.ROMM_SYNCED,
        activeVariantFileId = activeVariantFileId,
        lastPlayedFileId = lastPlayedFileId
    )

    private fun file(
        id: Long,
        gameId: Long,
        category: String,
        fileName: String = "File $id.sfc",
        isLaunchTarget: Boolean = true,
        versionGroup: String? = null
    ) = GameFileEntity(
        id = id,
        gameId = gameId,
        fileName = fileName,
        filePath = "/roms/snes/Game $gameId/$category/$fileName",
        category = category,
        fileSize = 8L,
        localPath = "/roms/snes/Game $gameId/$category/$fileName",
        isLaunchTarget = isLaunchTarget,
        versionGroup = versionGroup
    )

    @Test
    fun `clears selections pointing at files that cannot launch as a variant`() = runTest {
        coEvery { gameDao.getGamesWithFileSelection() } returns listOf(
            game(1L, activeVariantFileId = 11L, lastPlayedFileId = 12L),
            game(2L, lastPlayedFileId = 21L),
            game(3L, lastPlayedFileId = 31L),
            game(4L, activeVariantFileId = 99L)
        )
        coEvery { gameFileDao.getById(11L) } returns file(11L, 1L, "hack")
        coEvery { gameFileDao.getById(12L) } returns file(12L, 1L, "mod")
        coEvery { gameFileDao.getById(21L) } returns file(21L, 2L, "unknown", isLaunchTarget = false)
        coEvery { gameFileDao.getById(31L) } returns
            file(31L, 3L, "game", fileName = "Game 3.sfc", versionGroup = "romm:103")
        coEvery { gameFileDao.getById(99L) } returns null

        cleanup.runOnce()

        coVerify { gameDao.updateActiveVariantFileId(1L, null) }
        coVerify { gameDao.updateLastPlayedFileId(1L, null) }
        coVerify { gameDao.updateLastPlayedFileId(2L, null) }
        coVerify { gameDao.updateLastPlayedFileId(3L, null) }
        coVerify { gameDao.updateActiveVariantFileId(4L, null) }
    }

    @Test
    fun `keeps selections of translation, demo and prototype files`() = runTest {
        coEvery { gameDao.getGamesWithFileSelection() } returns listOf(
            game(1L, activeVariantFileId = 11L, lastPlayedFileId = 12L),
            game(2L, lastPlayedFileId = 21L)
        )
        coEvery { gameFileDao.getById(11L) } returns file(11L, 1L, "translation")
        coEvery { gameFileDao.getById(12L) } returns file(12L, 1L, "demo")
        coEvery { gameFileDao.getById(21L) } returns file(21L, 2L, "prototype")

        cleanup.runOnce()

        coVerify(exactly = 0) { gameDao.updateActiveVariantFileId(any(), any()) }
        coVerify(exactly = 0) { gameDao.updateLastPlayedFileId(any(), any()) }
    }

    @Test
    fun `demotes hack and mod rows and clears every versionGroup tag`() = runTest {
        val categories = slot<List<String>>()
        coEvery { gameFileDao.clearLaunchTargetForCategories(capture(categories)) } returns 2

        cleanup.runOnce()

        assertTrue("hack" in categories.captured)
        assertTrue("mod" in categories.captured)
        assertTrue("translation" !in categories.captured)
        assertTrue("demo" !in categories.captured)
        assertTrue("prototype" !in categories.captured)
        coVerify(exactly = 1) { gameFileDao.clearVersionGroups() }
        coVerify(exactly = 1) { appPreferences.setVariantFileCleanupDone() }
    }

    @Test
    fun `runs once`() = runTest {
        cleanup.runOnce()
        cleanup.runOnce()

        coVerify(exactly = 1) { gameFileDao.clearVersionGroups() }
        coVerify(exactly = 1) { gameDao.getGamesWithFileSelection() }
    }

    @Test
    fun `waits for the sibling split repair while version-grouped rows remain`() = runTest {
        coEvery { gameFileDao.hasVersionGroupedFiles() } returns true
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false

        cleanup.runOnce()

        coVerify(exactly = 0) { gameFileDao.clearVersionGroups() }
        coVerify(exactly = 0) { gameDao.getGamesWithFileSelection() }
        coVerify(exactly = 0) { appPreferences.setVariantFileCleanupDone() }
    }

    @Test
    fun `runs once the sibling split repair has finished`() = runTest {
        coEvery { gameFileDao.hasVersionGroupedFiles() } returns true
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true

        cleanup.runOnce()

        coVerify(exactly = 1) { gameFileDao.clearVersionGroups() }
        coVerify(exactly = 1) { appPreferences.setVariantFileCleanupDone() }
    }

    @Test
    fun `a failed run leaves the marker unset`() = runTest {
        coEvery { gameFileDao.clearVersionGroups() } throws IllegalStateException("db closed")

        cleanup.runOnce()

        coVerify(exactly = 0) { appPreferences.setVariantFileCleanupDone() }
    }
}
