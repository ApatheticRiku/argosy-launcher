package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncEngine
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncResult
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val GAME_ID = 51L
private const val EMULATOR = "builtin"
private const val CONFLICT_ID = 7L

class ResolveSnapshotConflictUseCaseTest {

    private val engine: SnapshotSyncEngine = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val gameRepository: GameRepository = mockk(relaxed = true)
    private val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
    private val pendingConflictDao: PendingConflictDao = mockk(relaxed = true)

    private val useCase = ResolveSnapshotConflictUseCase(
        engine, activeSaveRepository, gameRepository, saveSyncRepository, pendingConflictDao
    )

    @Test
    fun `each choice reaches its own engine call`() = runTest {
        coEvery { engine.keepLocal(GAME_ID, EMULATOR, null, false) } returns SnapshotSyncResult.Pushed(1)
        coEvery { engine.keepServer(GAME_ID, EMULATOR, null) } returns SnapshotSyncResult.Applied(2)
        coEvery { engine.revert(GAME_ID, EMULATOR, null) } returns SnapshotSyncResult.Applied(3)
        coEvery { engine.branch(GAME_ID, EMULATOR, null, false) } returns SnapshotSyncResult.Branched(4, "Thor 2026-10-08")

        assertEquals(SnapshotSyncResult.Pushed(1), useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.MINE))
        assertEquals(SnapshotSyncResult.Applied(2), useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.THEIRS))
        assertEquals(SnapshotSyncResult.Applied(3), useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.REVERT))
        assertEquals(
            SnapshotSyncResult.Branched(4, "Thor 2026-10-08"),
            useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.BRANCH)
        )
    }

    @Test
    fun `a branch becomes this device's active channel`() = runTest {
        coEvery { engine.branch(GAME_ID, EMULATOR, "default", false) } returns SnapshotSyncResult.Branched(4, "Thor 2026-10-08")

        useCase(GAME_ID, EMULATOR, "default", SnapshotConflictChoice.BRANCH)

        coVerify { activeSaveRepository.registerChannel(GAME_ID, "Thor 2026-10-08") }
        coVerify { activeSaveRepository.activateChannel(GAME_ID, "Thor 2026-10-08") }
    }

    @Test
    fun `a failed branch leaves the active channel alone`() = runTest {
        coEvery { engine.branch(GAME_ID, EMULATOR, "default", false) } returns SnapshotSyncResult.NoConnection

        useCase(GAME_ID, EMULATOR, "default", SnapshotConflictChoice.BRANCH)

        coVerify(exactly = 0) { activeSaveRepository.activateChannel(any(), any()) }
    }

    @Test
    fun `a hardcore save keeps its hardcore flag through keep mine and keep both`() = runTest {
        val hardcoreRow = io.mockk.mockk<com.nendo.argosy.data.local.entity.SaveCacheEntity>(relaxed = true) {
            io.mockk.every { isHardcore } returns true
        }
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns hardcoreRow
        coEvery { engine.keepLocal(GAME_ID, EMULATOR, "default", true) } returns SnapshotSyncResult.Pushed(1)
        coEvery { engine.branch(GAME_ID, EMULATOR, "default", true) } returns SnapshotSyncResult.Branched(2, "Thor 2026-10-08")

        useCase(GAME_ID, EMULATOR, "default", SnapshotConflictChoice.MINE)
        useCase(GAME_ID, EMULATOR, "default", SnapshotConflictChoice.BRANCH)

        coVerify { engine.keepLocal(GAME_ID, EMULATOR, "default", true) }
        coVerify { engine.branch(GAME_ID, EMULATOR, "default", true) }
    }

    @Test
    fun `a stored conflict closes only once its choice lands`() = runTest {
        coEvery { engine.keepLocal(GAME_ID, EMULATOR, null, false) } returns SnapshotSyncResult.NoConnection
        useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.MINE, pendingConflictId = CONFLICT_ID)
        coVerify(exactly = 0) { pendingConflictDao.dismiss(CONFLICT_ID) }

        coEvery { engine.keepLocal(GAME_ID, EMULATOR, null, false) } returns SnapshotSyncResult.Pushed(9)
        useCase(GAME_ID, EMULATOR, null, SnapshotConflictChoice.MINE, pendingConflictId = CONFLICT_ID)
        coVerify(exactly = 1) { pendingConflictDao.dismiss(CONFLICT_ID) }
    }

    @Test
    fun `a conflict with no emulator in hand takes the one its stored row names`() = runTest {
        coEvery { pendingConflictDao.getById(CONFLICT_ID) } returns mockk { io.mockk.every { emulator } returns EMULATOR }
        coEvery { engine.keepServer(GAME_ID, EMULATOR, null) } returns SnapshotSyncResult.Applied(2)

        assertEquals(
            SnapshotSyncResult.Applied(2),
            useCase(GAME_ID, null, null, SnapshotConflictChoice.THEIRS, pendingConflictId = CONFLICT_ID)
        )
    }
}
