package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMDeviceRegistration(
    @Json(name = "name") val name: String,
    @Json(name = "platform") val platform: String = "android",
    @Json(name = "client") val client: String = "argosy",
    @Json(name = "client_version") val clientVersion: String,
    @Json(name = "hostname") val hostname: String? = null,
    @Json(name = "sync_mode") val syncMode: String? = null,
    @Json(name = "sync_config") val syncConfig: Map<String, Any>? = null,
    @Json(name = "capabilities") val capabilities: Map<String, Boolean>? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceCapabilitiesUpdate(
    @Json(name = "capabilities") val capabilities: Map<String, Boolean>
)

private const val DEVICE_CAPABILITY_INSTALL = "install"

fun deviceCapabilities(allowRemoteInstalls: Boolean): Map<String, Boolean> =
    mapOf(DEVICE_CAPABILITY_INSTALL to allowRemoteInstalls)

@JsonClass(generateAdapter = true)
data class RomMDevice(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String?,
    @Json(name = "platform") val platform: String?,
    @Json(name = "client") val client: String?,
    @Json(name = "client_version") val clientVersion: String?,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceRegistrationResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceSync(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "last_synced_at") val lastSyncedAt: String? = null,
    @Json(name = "is_untracked") val isUntracked: Boolean = false,
    @Json(name = "is_current") val isCurrent: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMInstallRequest(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "file_ids") val fileIds: List<Long> = emptyList(),
    @Json(name = "rom_updated_at") val romUpdatedAt: String? = null,
    @Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class RomMInstallReport(
    @Json(name = "status") val status: String,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceIdRequest(
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class RomMDeviceAuthInitRequest(
    @Json(name = "client_device_identifier") val clientDeviceIdentifier: String,
    @Json(name = "name") val name: String,
    @Json(name = "client") val client: String = "argosy-launcher",
    @Json(name = "platform") val platform: String? = "Android",
    @Json(name = "client_version") val clientVersion: String? = null,
    @Json(name = "requested_scopes") val requestedScopes: List<String>
)

@JsonClass(generateAdapter = true)
data class RomMDeviceAuthInitResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_path") val verificationPath: String,
    @Json(name = "verification_path_complete") val verificationPathComplete: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "interval") val interval: Int
)

@JsonClass(generateAdapter = true)
data class RomMDeviceAuthTokenRequest(
    @Json(name = "device_code") val deviceCode: String
)

@JsonClass(generateAdapter = true)
data class RomMDeviceAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "scopes") val scopes: List<String> = emptyList(),
    @Json(name = "expires_at") val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDetailResponse(
    @Json(name = "detail") val detail: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMSaveConflictResponse(
    @Json(name = "detail") val detail: RomMSaveConflictDetail? = null
)

@JsonClass(generateAdapter = true)
data class RomMSaveConflictDetail(
    @Json(name = "error") val error: String,
    @Json(name = "message") val message: String,
    @Json(name = "save_id") val saveId: Long,
    @Json(name = "current_save_time") val currentSaveTime: String,
    @Json(name = "device_sync_time") val deviceSyncTime: String
)
