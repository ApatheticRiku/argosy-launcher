package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity

@Dao
interface SigilSyncStateDao {
    @Query(
        "SELECT * FROM sigil_sync_state WHERE ownerUserId = :ownerUserId AND platformSlug = :platformSlug " +
            "AND layout = :layout AND root = :root"
    )
    suspend fun get(ownerUserId: Long, platformSlug: String, layout: String, root: String): SigilSyncStateEntity?

    @Upsert
    suspend fun upsert(entity: SigilSyncStateEntity)
}
