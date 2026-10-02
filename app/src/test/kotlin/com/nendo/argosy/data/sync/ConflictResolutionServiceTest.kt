package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionServiceTest {
    private val pendingConflictDao: PendingConflictDao = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
    private val gameDao: com.nendo.argosy.data.local.dao.GameDao = mockk(relaxed = true)
    private val saveCacheManager: com.nendo.argosy.data.repository.SaveCacheManager = mockk(relaxed = true)
    private val service = ConflictResolutionService(pendingConflictDao, saveSyncRepository, gameDao) { saveCacheManager }

    @Test
    fun `keep local for a named slot uploads that slot's newest cached version, not the disk`() = runTest {
        val named = conflict.copy(slot = "Before boss")
        val older = com.nendo.argosy.data.local.entity.SaveCacheEntity(
            id = 20L, gameId = 1L, emulatorId = "retroarch", cachedAt = java.time.Instant.parse("2026-10-01T00:00:00Z"),
            saveSize = 3, cachePath = "1/a/save.srm", channelName = "Before boss", contentHash = "old"
        )
        val newest = older.copy(id = 21L, cachedAt = java.time.Instant.parse("2026-10-02T00:00:00Z"), contentHash = "new")
        val otherSlot = older.copy(id = 22L, cachedAt = java.time.Instant.parse("2026-10-03T00:00:00Z"), channelName = "autosave")
        coEvery { gameDao.getById(1L) } returns mockk(relaxed = true) { io.mockk.every { rommId } returns 100L }
        coEvery { saveCacheManager.getCachesForGameOnce(1L) } returns listOf(older, newest, otherSlot)
        val file = java.io.File("/cache/1/a/save.srm")
        io.mockk.every { saveCacheManager.getCacheFile(newest) } returns file
        coEvery { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SaveSyncResult.Success(rommSaveId = 11L)

        assertTrue(service.resolve(named, ConflictResolution.KEEP_LOCAL) is ConflictResolutionOutcome.Resolved)
        coVerify { saveSyncRepository.uploadCacheEntry(1L, 100L, "retroarch", "Before boss", file, "new", true, 21L, any()) }
        coVerify(exactly = 0) { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) }
    }

    private val conflict = PendingConflictEntity(
        id = 5L,
        gameId = 1L,
        rommSaveId = 9L,
        fileName = "autosave",
        slot = "autosave",
        emulator = "retroarch",
        localUpdatedAt = null,
        serverUpdatedAt = null
    )

    @Test
    fun `keep local dismisses the conflict once the upload succeeds`() = runTest {
        coEvery { saveSyncRepository.uploadSave(1L, "retroarch", "autosave", true, any(), any()) } returns SaveSyncResult.Success(rommSaveId = 10L)

        assertTrue(service.resolve(conflict, ConflictResolution.KEEP_LOCAL) is ConflictResolutionOutcome.Resolved)
        coVerify { pendingConflictDao.dismiss(5L) }
    }

    @Test
    fun `a failed upload leaves the conflict open`() = runTest {
        coEvery { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) } returns SaveSyncResult.Error("offline")

        assertTrue(service.resolve(conflict, ConflictResolution.KEEP_LOCAL) is ConflictResolutionOutcome.Failed)
        coVerify(exactly = 0) { pendingConflictDao.dismiss(any()) }
    }

    @Test
    fun `a download that needs a hardcore decision leaves the conflict open`() = runTest {
        coEvery { saveSyncRepository.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.NeedsHardcoreResolution(
            tempFilePath = "/tmp/x", gameId = 1L, gameName = "Game", emulatorId = "retroarch",
            targetPath = "/saves/x.srm", isFolderBased = false, channelName = "autosave"
        )

        assertTrue(service.resolve(conflict, ConflictResolution.KEEP_SERVER) is ConflictResolutionOutcome.Failed)
        coVerify(exactly = 0) { pendingConflictDao.dismiss(any()) }
    }

    @Test
    fun `keep server dismisses the conflict once the download succeeds`() = runTest {
        coEvery { saveSyncRepository.downloadSave(1L, "retroarch", "autosave", any(), 9L) } returns SaveSyncResult.Success()

        assertTrue(service.resolve(conflict, ConflictResolution.KEEP_SERVER) is ConflictResolutionOutcome.Resolved)
        coVerify { pendingConflictDao.dismiss(5L) }
    }
}
