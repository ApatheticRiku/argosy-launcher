package com.nendo.argosy.data.emulator

import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchProgressTrackerTest {

    private fun TestScope.tracker() = LaunchProgressTracker(backgroundScope)

    private val conflict = SyncProgress.LocalModified(gameId = 1L, localSavePath = "/save", channelName = null)

    @Test
    fun `a launch reports its steps under the game title`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("F-Zero GX")!!

        ticket.step(LaunchStep.PreparingSystemFiles)

        assertEquals("F-Zero GX", tracker.progress.value?.gameTitle)
        assertEquals(LaunchStep.PreparingSystemFiles, tracker.progress.value?.step)
    }

    @Test
    fun `a second launch is refused while one is in progress`() = runTest {
        val tracker = tracker()
        tracker.begin("First")

        assertNull(tracker.begin("Second"))
    }

    @Test
    fun `cancelling hides the launch and stops its later steps from showing`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!

        assertTrue(tracker.cancel())
        ticket.step(LaunchStep.PreparingUi)

        assertTrue(ticket.isCancelled)
        assertNull(tracker.progress.value)
    }

    @Test
    fun `cancelling a prompt answers it with nothing`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!
        val answer = async { ticket.ask(conflict, listOf(LaunchPromptOption.APPLY_LOCAL)) }
        runCurrent()

        tracker.cancel()

        assertNull(answer.await())
    }

    @Test
    fun `a prompt answer reaches the launch`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!
        val answer = async { ticket.ask(conflict, listOf(LaunchPromptOption.APPLY_LOCAL, LaunchPromptOption.RESTORE_SERVER)) }
        runCurrent()

        tracker.answer(LaunchPromptOption.RESTORE_SERVER)

        assertEquals(LaunchPromptOption.RESTORE_SERVER, answer.await())
    }

    @Test
    fun `a new launch waits for a cancelled one to finish before it starts`() = runTest {
        val tracker = tracker()
        val cancelled = tracker.begin("First")!!
        tracker.cancel()

        val next = async { tracker.begin("Second") }
        runCurrent()

        assertFalse(next.isCompleted)
        assertEquals(LaunchStep.FinishingPrevious, tracker.progress.value?.step)

        tracker.finish(cancelled, launched = false)

        val ticket = next.await()!!
        assertEquals(ticket, tracker.current)
    }

    @Test
    fun `a launch that opened its game shows until the screen that started it stops`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!

        tracker.finish(ticket, launched = true)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertFalse(tracker.cancel())

        tracker.hostStopped()
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch whose screen never stops clears on its own`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!

        tracker.finish(ticket, launched = true)
        advanceTimeBy(10_000)

        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch that failed clears at once`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin("Game")!!

        tracker.finish(ticket, launched = false)

        assertNull(tracker.progress.value)
        assertNull(tracker.current)
    }

    @Test
    fun `a launch may start while the last one is still showing its game opening`() = runTest {
        val tracker = tracker()
        tracker.finish(tracker.begin("First")!!, launched = true)

        val second = tracker.begin("Second")

        assertEquals(second, tracker.current)
    }
}
