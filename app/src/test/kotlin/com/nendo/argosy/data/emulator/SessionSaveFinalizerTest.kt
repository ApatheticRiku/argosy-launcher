package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.sync.SaveLookup
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SessionSaveFinalizerTest {
    private val gameId = 7L
    private val cacheId = 40L
    private val owner = 3L
    private val savePath = "/saves/test.srm"

    private val gameDao: GameDao = mockk(relaxed = true)
    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private val pendingConflictDao: PendingConflictDao = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val emulatorResolver: EmulatorResolver = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
    private val useCase: SyncSaveOnSessionEndUseCase = mockk(relaxed = true)

    private val finalizer = SessionSaveFinalizer(
        gameDao = gameDao,
        saveCacheDao = saveCacheDao,
        pendingSyncQueueDao = pendingSyncQueueDao,
        pendingConflictDao = pendingConflictDao,
        activeSaveRepository = activeSaveRepository,
        emulatorResolver = emulatorResolver,
        saveAccessNotices = mockk(relaxed = true),
        saveCacheManager = { saveCacheManager },
        saveSyncRepository = { saveSyncRepository },
        syncSaveOnSessionEnd = { useCase }
    )

    private val input = SessionSaveInput(
        gameId = gameId,
        emulatorPackage = "com.retroarch",
        coreName = "snes9x",
        isHardcore = false,
        channelName = "autosave",
        startTime = Instant.EPOCH,
        variantFileId = null,
        isNetplayGuest = false
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId,
            platformId = 1L,
            title = "Test Game",
            sortTitle = "test game",
            localPath = "/roms/test.sfc",
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED,
            platformSlug = "snes"
        )
        coEvery { emulatorResolver.resolveSessionEmulator(any(), any(), any(), any()) } returns
            SessionEmulator("com.retroarch", "retroarch")
        coEvery {
            saveSyncRepository.discoverSavePathChecked(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveLookup.Found(savePath)
        coEvery {
            saveCacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveCacheManager.CacheResult.Created(timestamp = 1L, cacheId = cacheId)
        coEvery { activeSaveRepository.activeOwnerId() } returns owner
    }

    private fun syncReturns(result: SyncSaveOnSessionEndUseCase.Result) {
        coEvery { useCase(any(), any(), any(), any(), any(), any()) } returns result
    }

    @Test
    fun `a failed cache keeps the session unsettled and leaves dirty flags alone`() = runTest {
        coEvery {
            saveCacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveCacheManager.CacheResult.Failed

        val outcome = finalizer.finalize(input)

        assertEquals(SessionSaveOutcome.CacheFailed, outcome)
        assertFalse(outcome.isSettled)
        coVerify(exactly = 0) { saveCacheDao.clearAllDirtyFlags(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.clearDirtyFlagForChannel(any(), any(), any(), any()) }
        coVerify(exactly = 0) { useCase(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an upload links the cache row, clears the channel and drops queued uploads`() = runTest {
        val serverTime = Instant.parse("2026-10-01T00:00:00Z")
        syncReturns(SyncSaveOnSessionEndUseCase.Result.Uploaded(rommSaveId = 9L, serverTimestamp = serverTime))

        val outcome = finalizer.finalize(input)

        assertTrue(outcome.isSettled)
        coVerify { activeSaveRepository.activateCache(gameId, cacheId) }
        coVerify { saveCacheDao.updateRommSaveId(cacheId, 9L) }
        coVerify { saveCacheDao.updateCachedAt(cacheId, serverTime) }
        coVerify { saveCacheDao.clearDirtyFlagForChannel(gameId, owner, "autosave", -1) }
        coVerify { pendingSyncQueueDao.deleteActiveByGameAndType(gameId, SyncType.SAVE_FILE, owner) }
    }

    @Test
    fun `a conflict is stored and the stored row owns the decision`() = runTest {
        val upload = SaveSyncResult.Conflict(
            gameId = gameId,
            localTimestamp = Instant.parse("2026-10-01T00:00:00Z"),
            serverTimestamp = Instant.parse("2026-10-02T00:00:00Z"),
            serverDeviceName = "ayn Odin3",
            serverSaveId = 55L,
            localContentHash = "local",
            serverContentHash = "server"
        )
        syncReturns(SyncSaveOnSessionEndUseCase.Result.Conflict(gameId, "retroarch", "autosave", upload))
        coEvery { pendingConflictDao.findByGameSaveAndOwner(any(), any(), any()) } returns null
        val stored = slot<PendingConflictEntity>()
        coEvery { pendingConflictDao.upsert(capture(stored)) } returns 12L

        val outcome = finalizer.finalize(input) as SessionSaveOutcome.Synced

        assertEquals(12L, outcome.conflictId)
        assertEquals(55L, stored.captured.rommSaveId)
        assertEquals("autosave", stored.captured.slot)
        assertEquals("retroarch", stored.captured.emulator)
        assertEquals(owner, stored.captured.ownerUserId)
        assertEquals("server", stored.captured.serverHash)
        coVerify { saveCacheDao.clearAllDirtyFlags(gameId, owner) }
    }

    @Test
    fun `a sync that never ran leaves dirty flags for the queue drain`() = runTest {
        syncReturns(SyncSaveOnSessionEndUseCase.Result.NotConfigured)

        val outcome = finalizer.finalize(input)

        assertTrue(outcome.isSettled)
        coVerify(exactly = 0) { saveCacheDao.clearAllDirtyFlags(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.clearDirtyFlagForChannel(any(), any(), any(), any()) }
    }

    @Test
    fun `a queued upload clears dirty flags`() = runTest {
        syncReturns(SyncSaveOnSessionEndUseCase.Result.Queued)

        finalizer.finalize(input)

        coVerify { saveCacheDao.clearAllDirtyFlags(gameId, owner) }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates instead of reading as a failed cache`() = runTest {
        coEvery {
            saveCacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws CancellationException("ended")

        finalizer.finalize(input)
    }

    @Test
    fun `netplay guest and variant sessions are exempt`() = runTest {
        assertEquals(SessionSaveOutcome.Exempt, finalizer.finalize(input.copy(isNetplayGuest = true)))
        assertEquals(SessionSaveOutcome.Exempt, finalizer.finalize(input.copy(variantFileId = 3L)))
        coVerify(exactly = 0) {
            saveCacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
