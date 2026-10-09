package com.nendo.argosy.ui.conflict

import com.nendo.argosy.domain.model.SnapshotConflictChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotChoiceRouteTest {

    @Test
    fun `with a stored conflict keep mine and use server go through the legacy resolver`() {
        assertEquals(SnapshotChoiceRoute.LEGACY_KEEP_LOCAL, routeSnapshotChoice(SnapshotConflictChoice.MINE, legacyResolves = true))
        assertEquals(SnapshotChoiceRoute.LEGACY_KEEP_SERVER, routeSnapshotChoice(SnapshotConflictChoice.THEIRS, legacyResolves = true))
    }

    @Test
    fun `without a stored conflict every choice goes to the snapshot engine`() {
        SnapshotConflictChoice.entries.forEach { choice ->
            assertEquals(choice.name, SnapshotChoiceRoute.SNAPSHOT, routeSnapshotChoice(choice, legacyResolves = false))
        }
    }

    @Test
    fun `keep both and revert always go to the snapshot engine`() {
        listOf(SnapshotConflictChoice.BRANCH, SnapshotConflictChoice.REVERT).forEach { choice ->
            assertEquals(choice.name, SnapshotChoiceRoute.SNAPSHOT, routeSnapshotChoice(choice, legacyResolves = true))
        }
    }

    @Test
    fun `a parked hardcore downgrade keeps its own dialog`() {
        assertFalse(offersSnapshotChoices(parkedDowngrade = true, snapshotServer = true))
        assertFalse(offersSnapshotChoices(parkedDowngrade = false, snapshotServer = false))
        assertTrue(offersSnapshotChoices(parkedDowngrade = false, snapshotServer = true))
    }
}
