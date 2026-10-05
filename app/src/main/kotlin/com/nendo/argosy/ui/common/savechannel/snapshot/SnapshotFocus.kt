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

    fun moveVertical(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val stops = state.stops
        if (stops.isEmpty()) return state
        val current = stops.indexOf(rowStop(state)).coerceAtLeast(0)
        return state.copy(stop = stops[(current + delta).mod(stops.size)])
    }

    /**
     * Left and right along one row, where an open channel's cards sit right after its tile:
     * stepping past the open tile enters its cards, and stepping past either end of the cards
     * returns to the tiles on that side.
     */
    fun moveHorizontal(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val row = rowStop(state)
        val isMine = row == SnapshotStop.MineTiles
        if (row != SnapshotStop.MineTiles && row != SnapshotStop.CommunityTiles) return state
        val tiles = if (isMine) state.mine else state.community
        if (tiles.isEmpty()) return state
        val open = state.expanded?.takeIf { it.isMine == isMine }
        val openIndex = open?.let { expanded -> tiles.indexOfFirst { it.channelId == expanded.channelId } } ?: -1
        val tileIndex = if (isMine) state.mineIndex else state.communityIndex
        fun onTile(index: Int) = if (isMine) {
            state.copy(stop = SnapshotStop.MineTiles, mineIndex = index.mod(tiles.size))
        } else {
            state.copy(stop = SnapshotStop.CommunityTiles, communityIndex = index.mod(tiles.size))
        }
        if (open == null || openIndex < 0 || open.stopCount == 0) return onTile(tileIndex + delta)
        if (state.stop == SnapshotStop.Cards) {
            val next = open.focusIndex + delta
            return when {
                next < 0 -> onTile(openIndex)
                next >= open.stopCount -> onTile(openIndex + 1)
                else -> state.copy(expanded = open.copy(focusIndex = next))
            }
        }
        return when {
            tileIndex == openIndex && delta > 0 -> state.copy(stop = SnapshotStop.Cards, expanded = open.copy(focusIndex = 0))
            tileIndex == (openIndex + 1).mod(tiles.size) && delta < 0 && tiles.size > 1 ->
                state.copy(stop = SnapshotStop.Cards, expanded = open.copy(focusIndex = open.stopCount - 1))
            else -> onTile(tileIndex + delta)
        }
    }

    fun moveOverlay(state: SnapshotViewState, delta: Int): SnapshotViewState {
        state.copyPicker?.let { picker ->
            if (picker.targets.isEmpty()) return state
            return state.copy(copyPicker = picker.copy(focusIndex = (picker.focusIndex + delta).mod(picker.targets.size)))
        }
        state.channelMenu?.let { menu ->
            if (menu.actions.isEmpty()) return state
            return state.copy(channelMenu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(menu.actions.size)))
        }
        state.detail?.let { detail ->
            if (detail.actions.isEmpty()) return state
            return state.copy(detail = detail.copy(focusIndex = (detail.focusIndex + delta).mod(detail.actions.size)))
        }
        return state
    }

    fun cycleStartOption(state: SnapshotViewState, delta: Int): SnapshotViewState {
        val entry = state.labelEntry ?: return state
        if (entry.startOptions.size < 2) return state
        return state.copy(labelEntry = entry.copy(startIndex = (entry.startIndex + delta).mod(entry.startOptions.size)))
    }
}
