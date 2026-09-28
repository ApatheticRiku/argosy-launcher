package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.GameGroupPickEntity

@Dao
interface GameGroupPickDao {

    @Query("SELECT * FROM game_group_picks WHERE ownerUserId IS :ownerUserId")
    suspend fun picksForOwner(ownerUserId: Long?): List<GameGroupPickEntity>

    @Query(
        "SELECT gameId FROM game_group_picks WHERE ownerUserId IS :ownerUserId AND groupKey = :groupKey"
    )
    suspend fun pickFor(ownerUserId: Long?, groupKey: String): Long?

    @Query("DELETE FROM game_group_picks WHERE ownerUserId IS :ownerUserId AND groupKey = :groupKey")
    suspend fun clear(ownerUserId: Long?, groupKey: String)

    @Query(
        "INSERT INTO game_group_picks (ownerUserId, groupKey, gameId) " +
            "VALUES (:ownerUserId, :groupKey, :gameId)"
    )
    suspend fun insert(ownerUserId: Long?, groupKey: String, gameId: Long)

    @Transaction
    suspend fun set(ownerUserId: Long?, groupKey: String, gameId: Long) {
        clear(ownerUserId, groupKey)
        insert(ownerUserId, groupKey, gameId)
    }

    @Transaction
    suspend fun insertAllMissing(ownerUserId: Long?, picks: Map<String, Long>) {
        picks.forEach { (groupKey, gameId) ->
            if (pickFor(ownerUserId, groupKey) == null) insert(ownerUserId, groupKey, gameId)
        }
    }

    @Query("DELETE FROM game_group_picks WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForOwner(ownerUserId: Long)
}
