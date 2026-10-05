package com.nendo.argosy.ui.screens.savetimeline

import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.sync.snapshot.SnapshotLibrary
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotCardUi
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotUiMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs

internal data class TimelineLaneLoad(
    val isLoading: Boolean,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val failed: Boolean = false
)

internal data class SaveTimelineLayout(
    val lanes: List<SaveTimelineLaneUi>,
    val columns: List<SaveTimelineColumnUi>,
    val forks: List<SaveTimelineForkUi>
)

internal object SaveTimelineBuilder {

    private data class PendingNode(
        val lane: Int,
        val channelId: String,
        val card: SnapshotCardUi,
        val at: Long,
        val parentSnapshotId: Long?
    ) {
        val key: String get() = "$channelId|${card.key}"
    }

    fun build(
        library: SnapshotLibrary,
        histories: Map<String, List<RomMSnapshot>>,
        loads: Map<String, TimelineLaneLoad>,
        mapper: SnapshotUiMapper
    ): SaveTimelineLayout {
        val entries = library.mine + library.community
        val pendingByLane = entries.mapIndexed { lane, entry ->
            val history = histories[entry.channel.id].orEmpty()
            val parents = history.associate { it.id to it.parentSnapshotId }
            mapper.cards(entry, history)
                .map { card ->
                    PendingNode(
                        lane = lane,
                        channelId = entry.channel.id,
                        card = card,
                        at = epochMillis(card.savedAt),
                        parentSnapshotId = card.snapshotId?.let(parents::get)
                    )
                }
                .sortedWith(compareBy<PendingNode>({ it.at }, { it.card.key }))
        }
        val ordered = pendingByLane.flatten()
            .sortedWith(compareBy<PendingNode>({ it.at }, { it.lane }, { it.card.key }))
        val nextPosition = IntArray(entries.size)
        val columns = ordered.map { node ->
            SaveTimelineColumnUi(key = node.key, lane = node.lane, position = nextPosition[node.lane]++)
        }
        val columnOf = ordered.withIndex().associate { (column, node) -> node.key to column }
        val placeOfSnapshot = ordered.withIndex()
            .mapNotNull { (column, node) -> node.card.snapshotId?.let { it to (node.lane to column) } }
            .toMap()
        val forks = ordered.withIndex().mapNotNull { (column, node) ->
            val (parentLane, parentColumn) = node.parentSnapshotId?.let(placeOfSnapshot::get)
                ?: return@mapNotNull null
            if (parentLane == node.lane) return@mapNotNull null
            SaveTimelineForkUi(
                parentLane = parentLane,
                parentColumn = parentColumn,
                childLane = node.lane,
                childColumn = column,
                isBranch = node.card.isBranch
            )
        }
        val lanes = entries.mapIndexed { lane, entry ->
            val channelId = entry.channel.id
            val load = loads[channelId]
                ?: TimelineLaneLoad(isLoading = entry.channel.current != null && channelId !in histories)
            SaveTimelineLaneUi(
                tile = mapper.tile(entry, library.deviceChannelId),
                nodes = pendingByLane[lane].map { node ->
                    SaveTimelineNodeUi(
                        card = node.card,
                        createdAtMillis = node.at,
                        column = columnOf.getValue(node.key)
                    )
                },
                isLoading = load.isLoading,
                isLoadingMore = load.isLoadingMore,
                hasMore = load.hasMore,
                failed = load.failed
            )
        }
        return SaveTimelineLayout(lanes = lanes, columns = columns, forks = forks)
    }

    fun closestPositionNewerOnTie(lane: SaveTimelineLaneUi, at: Long): Int {
        var best = -1
        var bestDistance = Long.MAX_VALUE
        lane.nodes.forEachIndexed { position, node ->
            val distance = abs(node.createdAtMillis - at)
            if (distance <= bestDistance) {
                best = position
                bestDistance = distance
            }
        }
        return best
    }

    private fun epochMillis(iso: String?): Long {
        val raw = iso ?: return 0L
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: 0L
    }
}
