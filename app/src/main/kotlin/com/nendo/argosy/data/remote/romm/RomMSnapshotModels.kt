package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMSnapshot(
    @Json(name = "id") val id: Long,
    @Json(name = "digest") val digest: String,
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "parent_snapshot_id") val parentSnapshotId: Long? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "is_hardcore") val isHardcore: Boolean = false,
    @Json(name = "is_pinned") val isPinned: Boolean = false,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "author_user_id") val authorUserId: Long? = null,
    @Json(name = "device") val device: RomMSnapshotDevice? = null,
    @Json(name = "channel") val channel: RomMSnapshotChannel? = null,
    @Json(name = "save") val save: RomMSnapshotSave? = null,
    @Json(name = "states") val states: Map<String, Map<String, RomMSnapshotState>> = emptyMap(),
    @Json(name = "thumbnail") val thumbnail: RomMScreenshotRef? = null
) {
    val isBranch: Boolean get() = kind == KIND_BRANCH

    companion object {
        const val KIND_BRANCH = "branch"
    }
}

@JsonClass(generateAdapter = true)
data class RomMSnapshotChannel(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "is_hardcore") val isHardcore: Boolean = false,
    @Json(name = "is_own") val isOwn: Boolean = true,
    @Json(name = "rom_file_id") val romFileId: Long? = null
)

@JsonClass(generateAdapter = true)
data class RomMChannel(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "is_hardcore") val isHardcore: Boolean = false,
    @Json(name = "is_own") val isOwn: Boolean = true,
    @Json(name = "owner_username") val ownerUsername: String? = null,
    @Json(name = "current_snapshot_id") val currentSnapshotId: Long? = null,
    @Json(name = "rom_file_id") val romFileId: Long? = null,
    @Json(name = "current") val current: RomMSnapshot? = null,
    @Json(name = "snapshot_count") val snapshotCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotDevice(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "client") val client: String? = null,
    @Json(name = "is_own") val isOwn: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMScreenshotRef(
    @Json(name = "id") val id: Long,
    @Json(name = "download_path") val downloadPath: String
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotSave(
    @Json(name = "id") val id: Long,
    @Json(name = "content_hash") val contentHash: String? = null,
    @Json(name = "identity_hash") val identityHash: String? = null,
    @Json(name = "shape") val shape: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long? = null,
    @Json(name = "download_path") val downloadPath: String? = null,
    @Json(name = "screenshot") val screenshot: RomMScreenshotRef? = null
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotState(
    @Json(name = "id") val id: Long,
    @Json(name = "content_hash") val contentHash: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "download_path") val downloadPath: String? = null,
    @Json(name = "screenshot") val screenshot: RomMScreenshotRef? = null
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotConflict(
    @Json(name = "current") val current: RomMSnapshotRef? = null,
    @Json(name = "hardcore_downgrade") val hardcoreDowngrade: Boolean = false,
    @Json(name = "missing") val missing: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotRef(
    @Json(name = "id") val id: Long,
    @Json(name = "digest") val digest: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMChannelCreate(
    @Json(name = "rom_file_id") val romFileId: Long,
    @Json(name = "label") val label: String,
    @Json(name = "id") val id: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMChannelUpdate(
    @Json(name = "label") val label: String? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RomMSnapshotUpdate(
    @Json(name = "is_pinned") val isPinned: Boolean? = null
)
