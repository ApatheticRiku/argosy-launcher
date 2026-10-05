package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity

@Dao
interface SnapshotChannelDao {
    @Query(
        "SELECT * FROM snapshot_channels WHERE ownerUserId = :ownerUserId AND gameId = :gameId " +
            "AND label = :label COLLATE NOCASE"
    )
    suspend fun get(ownerUserId: Long, gameId: Long, label: String): SnapshotChannelEntity?

    @Upsert
    suspend fun upsert(entity: SnapshotChannelEntity)
}
