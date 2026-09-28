package com.nendo.argosy.domain.usecase.cache

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.storage.StorageVolumeHealth
import com.nendo.argosy.data.storage.VolumeProbe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RepairImageCacheUseCaseTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val volumeHealth = mockk<StorageVolumeHealth>()
    private val probe = mockk<VolumeProbe>()
    private lateinit var useCase: RepairImageCacheUseCase

    @Before
    fun setup() {
        every { volumeHealth.newProbe() } returns probe
        coEvery { gameDao.getById(GAME_ID) } returns game()
        useCase = RepairImageCacheUseCase(gameDao, romMRepository, imageCacheManager, volumeHealth)
    }

    @Test
    fun `cover override on an unmounted volume is kept`() = runTest {
        every { probe.isGenuinelyAbsent(COVER_OVERRIDE) } returns false

        val repaired = useCase.repairCover(GAME_ID, COVER_OVERRIDE)

        assertEquals(COVER_PATH, repaired)
        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
    }

    @Test
    fun `cover override that is genuinely gone is cleared`() = runTest {
        every { probe.isGenuinelyAbsent(COVER_OVERRIDE) } returns true

        val repaired = useCase.repairCover(GAME_ID, COVER_OVERRIDE)

        assertEquals(COVER_PATH, repaired)
        coVerify(exactly = 1) { gameDao.clearCoverOverride(GAME_ID) }
    }

    @Test
    fun `background override on an unmounted volume is kept`() = runTest {
        every { probe.isGenuinelyAbsent(BACKGROUND_OVERRIDE) } returns false

        val repaired = useCase.repairBackground(GAME_ID, BACKGROUND_OVERRIDE)

        assertEquals(BACKGROUND_PATH, repaired)
        coVerify(exactly = 0) { gameDao.clearBackgroundOverride(any()) }
    }

    @Test
    fun `background override that is genuinely gone is cleared`() = runTest {
        every { probe.isGenuinelyAbsent(BACKGROUND_OVERRIDE) } returns true

        useCase.repairBackground(GAME_ID, BACKGROUND_OVERRIDE)

        coVerify(exactly = 1) { gameDao.clearBackgroundOverride(GAME_ID) }
    }

    private fun game() = GameEntity(
        id = GAME_ID,
        title = "Test",
        sortTitle = "test",
        platformId = 1L,
        platformSlug = "snes",
        rommId = null,
        igdbId = null,
        localPath = null,
        source = GameSource.ROMM_SYNCED,
        coverPath = COVER_PATH,
        backgroundPath = BACKGROUND_PATH,
        coverOverridePath = COVER_OVERRIDE,
        backgroundOverridePath = BACKGROUND_OVERRIDE
    )

    private companion object {
        const val GAME_ID = 7L
        const val COVER_PATH = "/data/user/0/com.nendo.argosy/cache/covers/cover_7.jpg"
        const val BACKGROUND_PATH = "/data/user/0/com.nendo.argosy/cache/bg/bg_7.jpg"
        const val COVER_OVERRIDE = "/storage/1A2B-3C4D/Argosy/art/cover_override_7_abc.jpg"
        const val BACKGROUND_OVERRIDE = "/storage/1A2B-3C4D/Argosy/art/bg_override_7_def.jpg"
    }
}
