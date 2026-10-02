package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedSaveEntry.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RestoreCachedSaveServerCachingTest {
    private val gameId = 7L
    private val serverSaveId = 42L
    private val downloadedCacheId = 900L
    private val localCacheId = 55L
    private val targetPath = "/saves/test.srm"

    private lateinit var saveCacheManager: SaveCacheManager
    private lateinit var saveSyncRepository: SaveSyncRepository
    private lateinit var gameDao: GameDao
    private lateinit var activeSaveRepository: ActiveSaveRepository
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var useCase: RestoreCachedSaveUseCase

    private val game = GameEntity(
        id = gameId,
        platformId = 1L,
        title = "Test Game",
        sortTitle = "test game",
        localPath = "/roms/test.gba",
        rommId = 100L,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun serverEntry() = UnifiedSaveEntry(
        serverSaveId = serverSaveId,
        timestamp = Instant.now(),
        size = 100,
        channelName = null,
        source = Source.SERVER
    )

    private fun localEntry() = UnifiedSaveEntry(
        localCacheId = localCacheId,
        timestamp = Instant.now(),
        size = 100,
        channelName = null,
        source = Source.LOCAL
    )

    @Before
    fun setup() {
        saveCacheManager = mockk(relaxed = true)
        saveSyncRepository = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        activeSaveRepository = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        useCase = RestoreCachedSaveUseCase(
            saveCacheManager,
            saveSyncRepository,
            gameDao,
            activeSaveRepository,
            emulatorResolver
        )

        coEvery { gameDao.getById(gameId) } returns game
        coEvery {
            saveSyncRepository.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any())
        } returns targetPath
        coEvery { saveSyncRepository.clearSavesBeforeRestore(any(), any(), any(), any()) } returns true
        coEvery { saveSyncRepository.downloadToCache(serverSaveId, gameId, null) } returns downloadedCacheId
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
        coEvery { saveCacheManager.restoreSave(any(), any()) } returns true
    }

    @Test
    fun `server restore downloads to cache, protects the disk, then writes and activates that cache row`() = runTest {
        val result = useCase(serverEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        assertEquals(RestoreCachedSaveUseCase.Result.Restored, result)
        coVerifyOrder {
            saveSyncRepository.downloadToCache(serverSaveId, gameId, null)
            saveCacheManager.protectBeforeOverwrite(gameId, "vbam", targetPath)
            saveSyncRepository.clearSavesBeforeRestore(targetPath, any(), any(), any())
            saveCacheManager.restoreSave(downloadedCacheId, targetPath)
            activeSaveRepository.activateCache(gameId, downloadedCacheId)
        }
    }

    @Test
    fun `failed server download leaves the disk untouched`() = runTest {
        coEvery { saveSyncRepository.downloadToCache(any(), any(), any()) } returns null

        val result = useCase(serverEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        assertEquals(
            RestoreCachedSaveUseCase.Result.Error(RestoreCachedSaveFailureReason.RestoreFailed),
            result
        )
        coVerify(exactly = 0) { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) }
        coVerify(exactly = 0) { saveSyncRepository.clearSavesBeforeRestore(any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `failed protection stops before the disk is cleared`() = runTest {
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns false

        val result = useCase(localEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        assertEquals(
            RestoreCachedSaveUseCase.Result.Error(RestoreCachedSaveFailureReason.ClearExistingSaveFailed),
            result
        )
        coVerify(exactly = 0) { saveSyncRepository.clearSavesBeforeRestore(any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `local restore activates the restored cache row`() = runTest {
        useCase(localEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        coVerify(exactly = 0) { saveSyncRepository.downloadToCache(any(), any(), any()) }
        coVerify(exactly = 1) { saveCacheManager.restoreSave(localCacheId, targetPath) }
        coVerify(exactly = 1) { activeSaveRepository.activateCache(gameId, localCacheId) }
    }
}
