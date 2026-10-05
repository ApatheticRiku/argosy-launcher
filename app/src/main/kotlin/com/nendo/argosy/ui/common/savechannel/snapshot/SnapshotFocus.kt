package com.nendo.argosy.ui.common.savechannel.snapshot

internal object SnapshotFocus {

    fun clampStop(state: SnapshotViewState, stop: SnapshotStop): SnapshotStop {
        val stops = state.stops
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
        val current = stops.indexOf(state.stop).coerceAtLeast(0)
        return state.copy(stop = stops[(current + delta).mod(stops.size)])
    }

    fun moveHorizontal(state: SnapshotViewState, delta: Int): SnapshotViewState =
        when (state.stop) {
            SnapshotStop.MineTiles ->
                if (state.mine.isEmpty()) state
                else state.copy(mineIndex = (state.mineIndex + delta).mod(state.mine.size))
            SnapshotStop.CommunityTiles ->
                if (state.community.isEmpty()) state
                else state.copy(communityIndex = (state.communityIndex + delta).mod(state.community.size))
            SnapshotStop.Cards -> {
                val expanded = state.expanded
                if (expanded == null || expanded.stopCount == 0) state
                else state.copy(expanded = expanded.copy(focusIndex = (expanded.focusIndex + delta).mod(expanded.stopCount)))
            }
            else -> state
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
