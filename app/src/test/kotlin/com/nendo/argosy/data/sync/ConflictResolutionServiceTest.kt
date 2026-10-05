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

    private fun version(id: Long, day: Int, hash: String, rommSaveId: Long? = null, channel: String = "Before boss") =
        com.nendo.argosy.data.local.entity.SaveCacheEntity(
            id = id, gameId = 1L, emulatorId = "retroarch",
            cachedAt = java.time.Instant.parse("2026-10-0${day}T00:00:00Z"),
            saveSize = 3, cachePath = "1/$id/save.srm", channelName = channel, contentHash = hash,
            rommSaveId = rommSaveId
        )

    private fun givenSlot(vararg versions: com.nendo.argosy.data.local.entity.SaveCacheEntity) {
        coEvery { gameDao.getById(1L) } returns mockk(relaxed = true) { io.mockk.every { rommId } returns 100L }
        coEvery { saveCacheManager.getCachesForGameOnce(1L) } returns versions.toList()
        versions.forEach { v -> io.mockk.every { saveCacheManager.getCacheFile(v) } returns java.io.File("/cache/${v.id}") }
        coEvery { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SaveSyncResult.Success(rommSaveId = 11L)
    }

    @Test
    fun `keep local for a named slot uploads every unsynced version oldest first, not the disk`() = runTest {
        givenSlot(version(20L, 1, "a"), version(21L, 2, "b"), version(22L, 3, "c", channel = "autosave"))

        assertTrue(service.resolve(conflict.copy(slot = "Before boss"), ConflictResolution.KEEP_LOCAL) is ConflictResolutionOutcome.Resolved)

        io.mockk.coVerifyOrder {
            saveSyncRepository.uploadCacheEntry(1L, 100L, "retroarch", "Before boss", java.io.File("/cache/20"), "a", true, 20L, any())
            saveSyncRepository.uploadCacheEntry(1L, 100L, "retroarch", "Before boss", java.io.File("/cache/21"), "b", false, 21L, any())
        }
        coVerify(exactly = 0) { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), 22L, any()) }
        coVerify(exactly = 0) { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `keeping this save over a hardcore one pushes it with the downgrade approved`() = runTest {
        givenSlot(version(20L, 1, "a"))
        coEvery { saveSyncRepository.approveHardcoreDowngrade(1L, "retroarch", null) } returns SaveSyncResult.Success()

        service.resolve(conflict.copy(slot = null, isHardcoreDowngrade = true), ConflictResolution.KEEP_LOCAL)

        coVerify { saveSyncRepository.approveHardcoreDowngrade(1L, "retroarch", null) }
        coVerify(exactly = 0) { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `versions older than the last synced one stay local`() = runTest {
        givenSlot(version(19L, 1, "old"), version(20L, 2, "synced", rommSaveId = 7L), version(21L, 3, "new"))

        service.resolve(conflict.copy(slot = "Before boss"), ConflictResolution.KEEP_LOCAL)

        coVerify(exactly = 1) { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), "new", true, 21L, any()) }
    }

    @Test
    fun `keep local keeps the other device's save in local history before uploading`() = runTest {
        givenSlot(version(21L, 2, "b"))

        service.resolve(conflict.copy(slot = "Before boss"), ConflictResolution.KEEP_LOCAL)

        io.mockk.coVerifyOrder {
            saveSyncRepository.downloadAndCacheSave(9L, 1L, "Before boss", false)
            saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), 21L, any())
        }
    }

    @Test
    fun `a failed link stops the chain`() = runTest {
        givenSlot(version(20L, 1, "a"), version(21L, 2, "b"))
        coEvery { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), 20L, any()) } returns SaveSyncResult.Error("offline")

        assertTrue(service.resolve(conflict.copy(slot = "Before boss"), ConflictResolution.KEEP_LOCAL) is ConflictResolutionOutcome.Failed)
        coVerify(exactly = 0) { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), 21L, any()) }
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
