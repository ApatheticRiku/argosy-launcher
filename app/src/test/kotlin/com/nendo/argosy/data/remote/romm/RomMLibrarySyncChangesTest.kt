package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.SyncFilterPreferences
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Duration
import java.time.Instant

class RomMLibrarySyncChangesTest {

    private val since = Instant.parse("2026-09-20T12:00:00Z")

    private lateinit var api: RomMApi
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var service: RomMLibrarySyncService

    private val platform = RomMPlatform(id = 1L, slug = "snes", name = "SNES", fsSlug = "snes", romCount = 1)

    private fun rom(platformId: Long = 1L) = RomMRom(
        id = 42L,
        platformId = platformId,
        platformSlug = "snes",
        name = "Chrono Trigger",
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

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns api
        every { preferences.boxArtCacheEnabled } returns false
        every { preferences.syncFilters } returns SyncFilterPreferences(deleteOrphans = false)
        every { preferencesRepository.preferences } returns flowOf(preferences)
        every {
            apiClient.buildRomsQueryParams(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { callOriginal() }
        every { apiClient.buildCoverUrls(any()) } returns emptyList()
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { api.getPlatforms() } returns Response.success(listOf(platform))
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
            every { syncEnabled } returns true
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
            imageCacheManager = mockk(relaxed = true),
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true),
            gameRepository = dagger.Lazy { mockk(relaxed = true) },
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = dagger.Lazy { mockk(relaxed = true) },
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = dagger.Lazy { mockk(relaxed = true) },
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            siblingSplitRepair = mockk(relaxed = true)
        )
    }

    @Test
    fun `the changes pass asks for roms changed since the last sync, with an overlap`() = runTest {
        val params = slot<Map<String, String>>()
        coEvery { api.getRoms(capture(params)) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))

        service.syncLibraryChanges(since)

        assertEquals(since.minus(Duration.ofHours(1)).toString(), params.captured["updated_after"])
        assertTrue("platform_ids" !in params.captured)
    }

    @Test
    fun `the changes pass syncs what came back and never marks rows dirty`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        val result = service.syncLibraryChanges(since)

        assertTrue("errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1, result.gamesAdded + result.gamesUpdated)
        coVerify(exactly = 0) { gameDao.markSyncDirtyForOwner(any(), any(), any()) }
        coVerify(exactly = 0) { gameDao.getSyncDirtyGames(any(), any()) }
    }

    @Test
    fun `a rom on a platform with sync turned off is skipped`() = runTest {
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { syncEnabled } returns false
        }
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = listOf(rom()), total = 1))

        val result = service.syncLibraryChanges(since)

        assertEquals(0, result.gamesAdded + result.gamesUpdated)
    }

    @Test
    fun `a failed fetch reports an error and keeps the last sync time`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.error(500, "".toResponseBody())

        val result = service.syncLibraryChanges(since)

        assertTrue(result.errors.isNotEmpty())
        coVerify(exactly = 0) { preferencesRepository.setLastRommSyncTime(any()) }
    }

    @Test
    fun `a clean pass records the time it started`() = runTest {
        coEvery { api.getRoms(any()) } returns Response.success(RomMRomPage(items = emptyList(), total = 0))
        val before = Instant.now()

        service.syncLibraryChanges(since)

        val recorded = slot<Instant>()
        coVerify { preferencesRepository.setLastRommSyncTime(capture(recorded)) }
        assertTrue(!recorded.captured.isBefore(before))
    }
}
