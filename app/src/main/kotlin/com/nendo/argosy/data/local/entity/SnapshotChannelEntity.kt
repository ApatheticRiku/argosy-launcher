package com.nendo.argosy.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "snapshot_channels",
    primaryKeys = ["ownerUserId", "gameId", "label"]
)
data class SnapshotChannelEntity(
    val ownerUserId: Long,
    val gameId: Long,
    val label: String,
    val channelId: String,
    val romFileId: Long,
    val heldSnapshotId: Long? = null,
    val heldDigest: String? = null,
    val heldSaveHash: String? = null,
    val heldSaveIdentityHash: String? = null,
    val updatedAt: Long
)
