package com.nendo.argosy.domain.usecase.cache

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.ArtSlot
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
    private val gameArtDao = mockk<GameArtDao>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val volumeHealth = mockk<StorageVolumeHealth>()
    private val probe = mockk<VolumeProbe>()
    private lateinit var useCase: RepairImageCacheUseCase

    @Before
    fun setup() {
        every { volumeHealth.newProbe() } returns probe
        coEvery { gameDao.getById(GAME_ID) } returns game()
        coEvery { gameArtDao.get(GAME_ID, ArtSlot.COVER.name) } returns
            GameArtEntity(GAME_ID, ArtSlot.COVER.name, cachedPath = COVER_PATH, overridePath = COVER_OVERRIDE)
        coEvery { gameArtDao.get(GAME_ID, ArtSlot.BACKGROUND.name) } returns
            GameArtEntity(GAME_ID, ArtSlot.BACKGROUND.name, cachedPath = BACKGROUND_PATH, overridePath = BACKGROUND_OVERRIDE)
        useCase = RepairImageCacheUseCase(gameDao, gameArtDao, romMRepository, imageCacheManager, volumeHealth)
    }

    @Test
    fun `cover override on an unmounted volume is kept`() = runTest {
        every { probe.isGenuinelyAbsent(COVER_OVERRIDE) } returns false

        val repaired = useCase.repairCover(GAME_ID, COVER_OVERRIDE)

        assertEquals(COVER_PATH, repaired)
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
    }

    @Test
    fun `cover override that is genuinely gone is cleared`() = runTest {
        every { probe.isGenuinelyAbsent(COVER_OVERRIDE) } returns true

        val repaired = useCase.repairCover(GAME_ID, COVER_OVERRIDE)

        assertEquals(COVER_PATH, repaired)
        coVerify(exactly = 1) { gameArtDao.updateOverride(GAME_ID, ArtSlot.COVER.name, null) }
    }

    @Test
    fun `background override on an unmounted volume is kept`() = runTest {
        every { probe.isGenuinelyAbsent(BACKGROUND_OVERRIDE) } returns false

        val repaired = useCase.repairBackground(GAME_ID, BACKGROUND_OVERRIDE)

        assertEquals(BACKGROUND_PATH, repaired)
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
    }

    @Test
    fun `background override that is genuinely gone is cleared`() = runTest {
        every { probe.isGenuinelyAbsent(BACKGROUND_OVERRIDE) } returns true

        useCase.repairBackground(GAME_ID, BACKGROUND_OVERRIDE)

        coVerify(exactly = 1) { gameArtDao.updateOverride(GAME_ID, ArtSlot.BACKGROUND.name, null) }
    }

    @Test
    fun `a missing cached cover is forgotten and its source queued again`() = runTest {
        every { probe.isGenuinelyAbsent(COVER_PATH) } returns true
        coEvery { gameArtDao.get(GAME_ID, ArtSlot.COVER.name) } returns
            GameArtEntity(GAME_ID, ArtSlot.COVER.name, sourceUrl = COVER_URL, cachedPath = COVER_PATH)

        val repaired = useCase.repairCover(GAME_ID, COVER_PATH)

        assertEquals(COVER_URL, repaired)
        coVerify(exactly = 1) { imageCacheManager.forgetCachedArt(GAME_ID, ArtSlot.COVER) }
        coVerify(exactly = 1) {
            imageCacheManager.queueArtIfStale(GAME_ID, ArtSlot.COVER, listOf(COVER_URL), null, null, "Test")
        }
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
        source = GameSource.ROMM_SYNCED
    )

    private companion object {
        const val GAME_ID = 7L
        const val COVER_URL = "https://romm/covers/7.png"
        const val COVER_PATH = "/data/user/0/com.nendo.argosy/cache/covers/cover_7.jpg"
        const val BACKGROUND_PATH = "/data/user/0/com.nendo.argosy/cache/bg/bg_7.jpg"
        const val COVER_OVERRIDE = "/storage/1A2B-3C4D/Argosy/art/cover_override_7_abc.jpg"
        const val BACKGROUND_OVERRIDE = "/storage/1A2B-3C4D/Argosy/art/bg_override_7_def.jpg"
    }
}
