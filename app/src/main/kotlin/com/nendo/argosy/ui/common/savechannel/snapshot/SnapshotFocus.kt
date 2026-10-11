package com.nendo.argosy.ui.common.savechannel.snapshot

internal object SnapshotFocus {

    private fun rowStop(state: SnapshotViewState): SnapshotStop =
        if (state.stop == SnapshotStop.Cards) {
            if (state.expanded?.isMine == false) SnapshotStop.CommunityTiles else SnapshotStop.MineTiles
        } else state.stop

    fun clampStop(state: SnapshotViewState, stop: SnapshotStop): SnapshotStop {
        val stops = state.stops
        if (stop == SnapshotStop.Cards && state.expanded != null) return stop
        if (stops.isEmpty() || stop in stops) return stop
        if (stop is SnapshotStop.Backup && state.backups.isNotEmpty()) {
            return SnapshotStop.Backup(stop.index.coerceAtMost(state.backups.lastIndex))
        }
        return stops.firstOrNull { it == SnapshotStop.MineTiles } ?: stops.first()
    }

    fun collapse(state: SnapshotViewState): SnapshotViewState {
        val open = state.expanded ?: return state
        val stop = if (open.isMine) SnapshotStop.MineTiles else SnapshotStop.CommunityTiles
        val collapsed = state.copy(expanded = null)
        return collapsed.copy(stop = clampStop(collapsed, stop))
    }

    private fun openIndex(state: SnapshotViewState, isMine: Boolean): Int {
        val open = state.expanded?.takeIf { it.isMine == isMine } ?: return -1
        val tiles = if (isMine) state.mine else state.community
        return tiles.indexOfFirst { it.channelId == open.channelId }
    }

    private fun currentRow(state: SnapshotViewState): Pair<SnapshotStop, Int> = when (val row = rowStop(state)) {
        SnapshotStop.MineTiles ->
            row to if (state.stop == SnapshotStop.Cards) openIndex(state, isMine = true) else state.mineIndex
        SnapshotStop.CommunityTiles ->
            row to if (state.stop == SnapshotStop.Cards) openIndex(state, isMine = false) else state.communityIndex
        else -> row to -1
    }

    /**
     * Up and down through one row per channel, then the backups and the community channels.
     */
    fun moveVertical(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val rows = buildList {
            state.stops.forEach { stop ->
                when (stop) {
                    SnapshotStop.MineTiles -> state.mine.indices.forEach { add(stop to it) }
                    SnapshotStop.CommunityTiles -> state.community.indices.forEach { add(stop to it) }
                    else -> add(stop to -1)
                }
            }
        }
        if (rows.isEmpty()) return state
        val current = rows.indexOf(currentRow(state)).coerceAtLeast(0)
        val (stop, index) = rows[(current + delta).mod(rows.size)]
        return when (stop) {
            SnapshotStop.MineTiles -> state.copy(stop = stop, mineIndex = index)
            SnapshotStop.CommunityTiles -> state.copy(stop = stop, communityIndex = index)
            else -> state.copy(stop = stop)
        }
    }

    /**
     * Left and right along a channel's row: right from an open channel's tile enters its cards,
     * and left from the first card returns to the tile.
     */
    fun moveHorizontal(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val (row, tileIndex) = currentRow(state)
        if (row != SnapshotStop.MineTiles && row != SnapshotStop.CommunityTiles) return state
        val isMine = row == SnapshotStop.MineTiles
        val open = state.expanded?.takeIf { it.isMine == isMine && openIndex(state, isMine) == tileIndex }
        if (open == null || open.stopCount == 0) return state
        if (state.stop == SnapshotStop.Cards) {
            val next = open.focusIndex + delta
            return when {
                next < 0 -> if (isMine) state.copy(stop = row, mineIndex = tileIndex)
                else state.copy(stop = row, communityIndex = tileIndex)
                next >= open.stopCount -> state
                else -> state.copy(expanded = open.copy(focusIndex = next))
            }
        }
        return if (delta > 0) state.copy(stop = SnapshotStop.Cards, expanded = open.copy(focusIndex = 0)) else state
    }

    fun moveOverlay(state: SnapshotViewState, delta: Int): SnapshotViewState {
        state.copyPicker?.let { picker ->
            if (picker.rowCount == 0) return state
            return state.copy(copyPicker = picker.copy(focusIndex = (picker.focusIndex + delta).mod(picker.rowCount)))
        }
        state.channelMenu?.let { menu ->
            if (menu.actions.isEmpty()) return state
            return state.copy(channelMenu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(menu.actions.size)))
        }
        state.detail?.let { detail ->
            if (detail.actions.isEmpty()) return state
            if (!detail.hasActivate) {
                return state.copy(detail = detail.copy(focusIndex = (detail.focusIndex + delta).mod(detail.actions.size)))
            }
            val column = detail.actions.size - 1
            if (detail.focusIndex == 0 || column == 0) return state
            return state.copy(detail = detail.copy(focusIndex = 1 + (detail.focusIndex - 1 + delta).mod(column)))
        }
        return state
    }

    /**
     * Left and right between a detail sheet's Activate button and the column of its other actions.
     */
    fun moveDetailColumn(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val detail = state.detail?.takeIf { it.hasActivate && it.actions.size > 1 } ?: return state
        if (state.channelMenu != null || state.copyPicker != null) return state
        val next = when {
            delta < 0 -> 0
            detail.focusIndex == 0 -> 1
            else -> detail.focusIndex
        }
        return state.copy(detail = detail.copy(focusIndex = next))
    }
}
