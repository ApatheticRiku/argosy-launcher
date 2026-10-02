package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveLookup
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SaveSyncOrchestratorDiskCheckTest {
    private val gameId = 1L
    private val emulatorId = "retroarch"
    private val savePath = "/saves/game.srm"
    private val owner = 3L

    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val savePathResolver: SavePathResolver = mockk(relaxed = true)
    private val apiClient: SaveSyncApiClient = mockk(relaxed = true)
    private val ownership: SaveOwnershipTracker = mockk(relaxed = true)

    private val orchestrator = SaveSyncOrchestrator(
        saveSyncDao = saveSyncDao,
        saveCacheDao = saveCacheDao,
        saveCacheManager = { saveCacheManager },
        activeSaveRepository = activeSaveRepository,
        pendingSyncQueueDao = mockk(relaxed = true),
        gameDao = gameDao,
        emulatorResolver = mockk(relaxed = true),
        savePathResolver = savePathResolver,
        userPreferencesRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        syncQueueManager = SyncQueueManager(),
        apiClient = { apiClient },
        payloadCodec = com.nendo.argosy.data.sync.SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
        saveHandlerRegistry = mockk(relaxed = true),
        saveAccessNotices = com.nendo.argosy.data.sync.SaveAccessNotices(),
        saveOwnershipTracker = ownership,
        accountSwitchMarkerStore = mockk(relaxed = true),
        fileAccessLayer = mockk(relaxed = true)
    )

    private val active = SaveCacheEntity(
        id = 50L,
        gameId = gameId,
        emulatorId = emulatorId,
        cachedAt = Instant.parse("2026-10-01T00:00:00Z"),
        saveSize = 10,
        cachePath = "x/game.srm",
        contentHash = "server-form",
        identityHash = "server-form",
        channelName = "autosave",
        rommSaveId = 9L
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId,
            platformId = 1L,
            title = "Game",
            sortTitle = "game",
            localPath = "/roms/game.sfc",
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED,
            platformSlug = "snes"
        )
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active
        coEvery { saveCacheDao.getMostRecent(gameId, owner) } returns active
        coEvery {
            savePathResolver.discoverSavePathChecked(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveLookup.Found(savePath)
        coEvery { saveCacheManager.calculateLocalSaveHash(savePath, gameId, emulatorId) } returns "disk"
        coEvery { saveCacheManager.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, ROLLBACK_ID)
        coEvery { saveCacheManager.restoreSave(any(), any()) } returns true
        coEvery { apiClient.clearSavesBeforeRestore(any(), any(), any(), any()) } returns true
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { ownership.claim(any(), any()) } returns SaveClaim.Unowned
    }

    private suspend fun check(secureSaves: Boolean) =
        orchestrator.checkDiskAgainstActive(gameId, emulatorId, null, secureSaves, owner)

    @Test
    fun `Secure Saves on restores the active version over a differing disk, after protecting the disk`() = runTest {
        assertEquals(SaveSyncOrchestrator.DiskCheck.Restored, check(secureSaves = true))
        coVerifyOrder {
            saveCacheManager.cacheAsRollback(gameId, emulatorId, savePath)
            apiClient.clearSavesBeforeRestore(savePath, "snes", any(), any())
            saveCacheManager.restoreSave(active.id, savePath)
        }
    }

    @Test
    fun `Secure Saves on never overwrites a disk it could not protect`() = runTest {
        coEvery { saveCacheManager.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Failed

        assertEquals(SaveSyncOrchestrator.DiskCheck.Failed, check(secureSaves = true))
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
        coVerify(exactly = 0) { apiClient.clearSavesBeforeRestore(any(), any(), any(), any()) }
    }

    @Test
    fun `a restore that fails after the clear puts the disk save back from its backup`() = runTest {
        coEvery { saveCacheManager.restoreSave(active.id, savePath) } returns false

        assertEquals(SaveSyncOrchestrator.DiskCheck.Failed, check(secureSaves = true))
        coVerify { saveCacheManager.restoreSave(ROLLBACK_ID, savePath) }
    }

    @Test
    fun `a failed clear puts the disk save back from its backup`() = runTest {
        coEvery { apiClient.clearSavesBeforeRestore(any(), any(), any(), any()) } returns false

        assertEquals(SaveSyncOrchestrator.DiskCheck.Failed, check(secureSaves = true))
        coVerify(exactly = 0) { saveCacheManager.restoreSave(active.id, any()) }
        coVerify { saveCacheManager.restoreSave(ROLLBACK_ID, savePath) }
    }

    @Test
    fun `Secure Saves off adopts a differing disk as a new dirty version`() = runTest {
        assertEquals(SaveSyncOrchestrator.DiskCheck.Adopted, check(secureSaves = false))
        coVerify {
            saveCacheManager.cacheCurrentSave(gameId, emulatorId, savePath, null, any(), any(), any(), any(), any(), true, any(), any())
        }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `a disk holding the local form of the last transferred version matches`() = runTest {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave", owner) } returns SaveSyncEntity(
            id = 4L, gameId = gameId, rommId = 100L, emulatorId = emulatorId, channelName = "autosave",
            rommSaveId = 9L, localContentHash = "disk", syncStatus = SaveSyncEntity.STATUS_SYNCED
        )

        assertEquals(SaveSyncOrchestrator.DiskCheck.Matches, check(secureSaves = true))
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
        coVerify(exactly = 0) { saveCacheManager.cacheAsRollback(any(), any(), any()) }
    }

    @Test
    fun `a disk matching a newer unsynced version is progress and becomes active`() = runTest {
        val unsynced = active.copy(id = 61L, cachedAt = Instant.parse("2026-10-02T00:00:00Z"), contentHash = "disk", needsRemoteSync = true)
        coEvery { saveCacheDao.getAllByGameChannelAndHash(gameId, owner, "autosave", "disk") } returns listOf(unsynced)

        assertEquals(SaveSyncOrchestrator.DiskCheck.Adopted, check(secureSaves = true))
        coVerify { activeSaveRepository.activateCache(gameId, 61L) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `no save on disk for this emulator places the active version at the constructed save file`() = runTest {
        coEvery {
            savePathResolver.discoverSavePathChecked(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveLookup.Absent
        val constructed = "/storage/emulated/0/RetroArch/saves/mGBA/game.srm"
        coEvery { savePathResolver.constructSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns constructed

        assertEquals(SaveSyncOrchestrator.DiskCheck.Restored, check(secureSaves = false))
        coVerify(exactly = 1) { saveCacheManager.restoreSave(active.id, constructed) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(active.id, match { "{" in it }) }
    }

    @Test
    fun `a hardcore active version is left to the hardcore gate`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(gameId) } returns active.copy(isHardcore = true)

        assertEquals(SaveSyncOrchestrator.DiskCheck.Untouched, check(secureSaves = true))
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any()) }
    }

    private companion object {
        const val ROLLBACK_ID = 99L
    }
}
