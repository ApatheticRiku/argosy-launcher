package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sigil_sync_state",
    primaryKeys = ["ownerUserId", "platformSlug", "layout", "root"]
)
data class SigilSyncStateEntity(
    val ownerUserId: Long,
    val platformSlug: String,
    val layout: String,
    val root: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val state: ByteArray,
    val unowned: String,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean =
        other is SigilSyncStateEntity &&
            ownerUserId == other.ownerUserId &&
            platformSlug == other.platformSlug &&
            layout == other.layout &&
            root == other.root &&
            state.contentEquals(other.state) &&
            unowned == other.unowned &&
            updatedAt == other.updatedAt

    override fun hashCode(): Int =
        listOf(ownerUserId, platformSlug, layout, root, state.contentHashCode(), unowned, updatedAt).hashCode()

    companion object {
        const val NO_OWNER = 0L
    }
}
