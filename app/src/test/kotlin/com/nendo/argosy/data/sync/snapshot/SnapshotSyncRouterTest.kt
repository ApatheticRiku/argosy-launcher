package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.repository.SaveSyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSyncRouterTest {

    private val engine = mockk<SnapshotSyncEngine>()
    private val router = SnapshotSyncRouter(engine)

    @Test
    fun `a refused softcore push over a hardcore current parks as a hardcore conflict`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.sync(5, "argosy", null, false) } returns SnapshotSyncResult.HardcoreDowngrade(41)

        val result = router.upload(5, "argosy", null, keepLocal = false, isHardcore = false)

        assertTrue((result as SaveSyncResult.Conflict).isHardcoreDowngrade)
    }

    @Test
    fun `approving the downgrade pushes the local save with the approval`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.keepLocal(5, "argosy", null, approveHardcoreDowngrade = true) } returns SnapshotSyncResult.Pushed(42)

        router.approveHardcoreDowngrade(5, "argosy", null)

        coVerify { engine.keepLocal(5, "argosy", null, false, true) }
    }
}
