package com.nendo.argosy.domain.model

import com.nendo.argosy.data.local.entity.SaveSyncEntity

enum class SaveListState {
    SYNCED, LOCAL_AHEAD, SERVER_AHEAD, NEEDS_ATTENTION;

    companion object {
        fun of(syncStatus: String): SaveListState? = when (syncStatus) {
            SaveSyncEntity.STATUS_SYNCED -> SYNCED
            SaveSyncEntity.STATUS_LOCAL_NEWER, SaveSyncEntity.STATUS_PENDING_UPLOAD -> LOCAL_AHEAD
            SaveSyncEntity.STATUS_SERVER_NEWER -> SERVER_AHEAD
            SaveSyncEntity.STATUS_CONFLICT, SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION -> NEEDS_ATTENTION
            else -> null
        }

        fun worstOf(syncStatuses: Iterable<String>): SaveListState? =
            syncStatuses.mapNotNull(::of).maxOrNull()

        fun worstByGame(rows: Iterable<Pair<Long, String>>): Map<Long, SaveListState> =
            rows.groupBy({ it.first }, { it.second })
                .mapNotNull { (gameId, statuses) -> worstOf(statuses)?.let { gameId to it } }
                .toMap()
    }
}
