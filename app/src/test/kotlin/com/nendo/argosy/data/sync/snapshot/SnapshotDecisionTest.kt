package com.nendo.argosy.data.sync.snapshot

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDecisionTest {

    private val a = SaveHashes("content-a", "identity-a")
    private val b = SaveHashes("content-b", "identity-b")
    private val aClockTicked = SaveHashes("content-a2", "identity-a")

    private fun point(id: Long, save: SaveHashes?) = SnapshotPoint(id, save)

    @Test
    fun `holding current with clean files does nothing`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, a), point(41, a), a))
    }

    @Test
    fun `a clock-only change is not dirty`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, a), point(41, a), aClockTicked))
    }

    @Test
    fun `holding current with dirty files pushes expecting current`() {
        assertEquals(SnapshotAction.Push(41), SnapshotDecision.decide(point(41, a), point(41, a), b))
    }

    @Test
    fun `holding an older snapshot with clean files downloads current`() {
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(point(41, a), point(42, b), a))
    }

    @Test
    fun `holding an older snapshot with dirty files is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(point(41, a), point(42, b), aClockTicked.copy(identityHash = "identity-c")))
    }

    @Test
    fun `holding nothing with no files downloads current`() {
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(null, point(42, b), null))
    }

    @Test
    fun `holding nothing with files equal to current adopts it`() {
        assertEquals(SnapshotAction.Adopt(42), SnapshotDecision.decide(null, point(42, b), b))
    }

    @Test
    fun `holding nothing with files different from current is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(null, point(42, b), a))
    }

    @Test
    fun `holding nothing with files against a current that has no save is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(null, point(42, null), a))
    }

    @Test
    fun `a channel with no current and files on disk pushes expecting null`() {
        assertEquals(SnapshotAction.Push(null), SnapshotDecision.decide(null, null, a))
    }

    @Test
    fun `no current and no files does nothing`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(null, null, null))
    }

    @Test
    fun `a save missing locally never pushes`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, a), point(41, a), null))
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(point(41, a), point(42, b), null))
    }

    @Test
    fun `a first save on top of a snapshot without one is dirty`() {
        assertEquals(SnapshotAction.Push(41), SnapshotDecision.decide(point(41, null), point(41, null), a))
    }
}
