package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AndroidGameScannerCountTest {

    private val appsRepository: AppsRepository = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val platformDao: PlatformDao = mockk(relaxed = true)
    private val syncPreferences: SyncPreferencesRepository = mockk(relaxed = true)

    private val scanner = AndroidGameScanner(
        appsRepository = appsRepository,
        appCategoryDao = mockk<AppCategoryDao>(relaxed = true),
        gameDao = gameDao,
        platformDao = platformDao,
        syncPreferencesRepository = syncPreferences,
        metadataFetcher = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true)
    )

    @Test
    fun `a scan that adds nothing still corrects a stale platform count`() = runBlocking {
        coEvery { syncPreferences.getRommUserId() } returns null
        coEvery { platformDao.getById(LocalPlatformIds.ANDROID) } returns mockk(relaxed = true)
        coEvery { gameDao.getByPlatform(LocalPlatformIds.ANDROID, null) } returns emptyList()
        coEvery { appsRepository.getInstalledApps(includeSystemApps = false) } returns emptyList()
        coEvery { gameDao.countByPlatform(LocalPlatformIds.ANDROID, null) } returns 3

        scanner.scanInstalledGames()

        coVerify { platformDao.updateGameCount(LocalPlatformIds.ANDROID, 3) }
    }

    @Test
    fun `refreshing writes the current count of Android games`() = runBlocking {
        coEvery { syncPreferences.getRommUserId() } returns 7L
        coEvery { gameDao.countByPlatform(LocalPlatformIds.ANDROID, 7L) } returns 0

        scanner.refreshGameCount()

        coVerify { platformDao.updateGameCount(LocalPlatformIds.ANDROID, 0) }
    }
}
