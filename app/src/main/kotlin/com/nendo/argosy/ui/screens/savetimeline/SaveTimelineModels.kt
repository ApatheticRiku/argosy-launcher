package com.nendo.argosy.ui.screens.savetimeline

import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotCardUi
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotTileUi

data class SaveTimelineNodeUi(
    val card: SnapshotCardUi,
    val createdAtMillis: Long,
    val column: Int
)

data class SaveTimelineLaneUi(
    val tile: SnapshotTileUi,
    val nodes: List<SaveTimelineNodeUi>,
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val failed: Boolean
) {
    val channelId: String get() = tile.channelId
    val oldestSnapshotPosition: Int get() = nodes.indexOfFirst { !it.card.isOlderClient }
}

data class SaveTimelineForkUi(
    val parentLane: Int,
    val parentColumn: Int,
    val childLane: Int,
    val childColumn: Int,
    val isBranch: Boolean
)

data class SaveTimelineColumnUi(val key: String, val lane: Int, val position: Int)

data class SaveTimelineHeaderUi(val title: String, val platformName: String?)

data class SaveTimelineUiState(
    val gameId: Long = 0,
    val header: SaveTimelineHeaderUi? = null,
    val deviceChannel: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val lanes: List<SaveTimelineLaneUi> = emptyList(),
    val columns: List<SaveTimelineColumnUi> = emptyList(),
    val forks: List<SaveTimelineForkUi> = emptyList(),
    val focusLane: Int = -1,
    val focusPosition: Int = -1,
    val recenterTick: Int = 0
) {
    val focusedLane: SaveTimelineLaneUi? get() = lanes.getOrNull(focusLane)
    val focusedNode: SaveTimelineNodeUi? get() = focusedLane?.nodes?.getOrNull(focusPosition)

    fun isFocused(lane: Int, position: Int): Boolean = lane == focusLane && position == focusPosition
}
