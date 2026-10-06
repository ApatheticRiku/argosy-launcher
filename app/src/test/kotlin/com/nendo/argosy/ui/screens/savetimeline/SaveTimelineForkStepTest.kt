package com.nendo.argosy.ui.screens.savetimeline

import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotCardUi
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotTileUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveTimelineForkStepTest {

    private fun node(column: Int) = SaveTimelineNodeUi(
        card = SnapshotCardUi(
            key = "s-$column", snapshotId = column.toLong(), saveId = null, savedAt = null, device = null,
            fileName = null, thumbnailUrl = null, isCurrent = false, isBranch = false, isPinned = false,
            isHardcore = false
        ),
        createdAtMillis = column.toLong(),
        column = column
    )

    private fun lane(id: String, vararg columns: Int, hasMore: Boolean = false) = SaveTimelineLaneUi(
        tile = SnapshotTileUi(
            channelId = id, label = id, thumbnailUrl = null, isOwn = true, isHardcore = false, isShared = false,
            ownerName = null, hasOnlyOlderSaves = false, isDeviceChannel = false, savedAt = null, device = null
        ),
        nodes = columns.map(::node),
        isLoading = false,
        isLoadingMore = false,
        hasMore = hasMore,
        failed = false
    )

    private val default = lane("default", 0, 1, 4)
    private val main = lane("main", 2, 3)
    private val fork = SaveTimelineForkUi(parentLane = 0, parentColumn = 1, childLane = 1, childColumn = 2, isBranch = false)

    private fun state(lanes: List<SaveTimelineLaneUi>, lane: Int, position: Int) = SaveTimelineUiState(
        lanes = lanes, forks = listOf(fork), focusLane = lane, focusPosition = position
    )

    @Test
    fun `left past a fork's oldest snapshot lands on its parent`() {
        assertEquals(0 to 1, SaveTimelineBuilder.forkStep(state(listOf(default, main), lane = 1, position = 0), -1))
    }

    @Test
    fun `left past the oldest snapshot waits for older history before following the fork`() {
        val paging = lane("main", 2, 3, hasMore = true)
        assertNull(SaveTimelineBuilder.forkStep(state(listOf(default, paging), lane = 1, position = 0), -1))
    }

    @Test
    fun `right past the newest snapshot follows a fork made from it`() {
        val parentAtEnd = lane("default", 0, 1)
        assertEquals(1 to 0, SaveTimelineBuilder.forkStep(state(listOf(parentAtEnd, main), lane = 0, position = 1), 1))
    }

    @Test
    fun `an end that joins nothing stays put`() {
        assertNull(SaveTimelineBuilder.forkStep(state(listOf(default, main), lane = 0, position = 2), 1))
        assertNull(SaveTimelineBuilder.forkStep(state(listOf(default, main), lane = 0, position = 0), -1))
    }
}
