package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMLibrarySyncArtOverrideTest {

    private val romId = 42L
    private val romName = "Chrono Trigger"

    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var overlayWriter: GameUserOverlayWriter
    private lateinit var service: RomMLibrarySyncService

    private fun rom() = RomMRom(
        id = romId,
        platformId = 1L,
        platformSlug = "snes",
        name = romName,
        slug = "chrono-trigger",
        fileName = "ct.sfc",
        filePath = "/roms/snes/ct.sfc",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null
    )

    private fun existing(
        coverOverridePath: String? = "/cache/snes/covers/cover_override_9_a.jpg",
        backgroundOverridePath: String? = "/cache/snes/backgrounds/bg_override_9_b.jpg",
        logoOverridePath: String? = "/cache/snes/logos/logo_override_9_c.png"
    ) = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "snes",
        title = romName,
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = romId,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        coverPath = "/cache/snes/covers/cover_42_server.jpg",
        backgroundPath = "/cache/snes/backgrounds/bg_42_server.jpg",
        logoPath = "/cache/snes/covers/game_logo_42_server.png",
        coverOverridePath = coverOverridePath,
        backgroundOverridePath = backgroundOverridePath,
        logoOverridePath = logoOverridePath
    )

    @Before
    fun setup() {
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        overlayWriter = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)
        val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)

        every { connectionManager.getApi() } returns mockk(relaxed = true)
        every { preferences.boxArtCacheEnabled } returns false
        every { preferencesRepository.preferences } returns flowOf(preferences)
        coEvery { apiClient.getRom(romId) } returns RomMResult.Success(rom())
        every { apiClient.buildCoverUrls(any()) } returns emptyList()
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
        }
        coEvery { overlayWriter.activeOwnerId() } returns null

        service = RomMLibrarySyncService(
            apiClient = apiClient,
            connectionManager = connectionManager,
            userPreferencesRepository = preferencesRepository,
            database = mockk(relaxed = true),
            gameDao = gameDao,
            homeTileDao = mockk(relaxed = true),
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            stateCacheDao = mockk(relaxed = true),
            platformDao = platformDao,
            emulatorConfigDao = mockk(relaxed = true),
            platformLibretroSettingsDao = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            firmwareDao = mockk(relaxed = true),
            controllerMappingDao = mockk(relaxed = true),
            collectionDao = mockk(relaxed = true),
            imageCacheManager = imageCacheManager,
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true),
            gameRepository = mockk(relaxed = true),
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = mockk(relaxed = true),
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            siblingSplitRepair = mockk(relaxed = true)
        )
    }

    @Test
    fun `a sync upsert carries every override from the existing row`() = runTest {
        val stored = existing()
        coEvery { gameDao.getByRommId(romId) } returns stored
        val written = slot<GameEntity>()
        coEvery { gameDao.insert(capture(written)) } returns stored.id

        service.syncSingleRom(romId)

        assertEquals(stored.coverOverridePath, written.captured.coverOverridePath)
        assertEquals(stored.backgroundOverridePath, written.captured.backgroundOverridePath)
        assertEquals(stored.logoOverridePath, written.captured.logoOverridePath)
    }

    @Test
    fun `a sync keeps writing the server columns beside an override`() = runTest {
        val stored = existing()
        coEvery { gameDao.getByRommId(romId) } returns stored
        val written = slot<GameEntity>()
        coEvery { gameDao.insert(capture(written)) } returns stored.id

        service.syncSingleRom(romId)

        assertEquals(stored.coverPath, written.captured.coverPath)
        assertEquals(stored.backgroundPath, written.captured.backgroundPath)
        assertEquals(stored.logoPath, written.captured.logoPath)
    }

    @Test
    fun `a row without overrides stays without them`() = runTest {
        val stored = existing(null, null, null)
        coEvery { gameDao.getByRommId(romId) } returns stored
        val written = slot<GameEntity>()
        coEvery { gameDao.insert(capture(written)) } returns stored.id

        service.syncSingleRom(romId)

        assertTrue(written.isCaptured)
        assertEquals(null, written.captured.coverOverridePath)
        assertEquals(null, written.captured.backgroundOverridePath)
        assertEquals(null, written.captured.logoOverridePath)
        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
    }
}
