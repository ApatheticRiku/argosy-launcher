package com.nendo.argosy.ui.screens.savesync

import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.domain.model.DeviceKind
import java.time.Instant

enum class AttentionAction { KEEP_LOCAL, KEEP_SERVER, SKIP }

sealed class ForceSaveCheckUiState {
    data object Idle : ForceSaveCheckUiState()
    data object Running : ForceSaveCheckUiState()
    data class Complete(
        val inspected: Int,
        val queued: Int,
        val downloaded: Int,
        val message: String?
    ) : ForceSaveCheckUiState()
    data class Failed(val message: String) : ForceSaveCheckUiState()
}

data class SaveAccessNoticeUi(
    val count: Int,
    val emulatorNames: List<String>
)

data class SaveSyncUiState(
    val deviceCard: ThisDeviceCard = ThisDeviceCard(),
    val otherDevices: List<DeviceSummary> = emptyList(),
    val otherDevicesExpanded: Boolean = false,
    val accessNotice: SaveAccessNoticeUi? = null,
    val attentionRows: List<AttentionRow> = emptyList(),
    val inProgressRows: List<InProgressRow> = emptyList(),
    val gameRows: List<GameSaveRow> = emptyList(),
    val focusedRowKey: String? = null,
    val attentionAction: AttentionAction = AttentionAction.SKIP,
    val isLoading: Boolean = true
) {
    val devicesRow: OtherDevicesRow?
        get() = if (otherDevices.isEmpty()) null else OtherDevicesRow

    val contentRows: List<SaveSyncRow>
        get() = attentionRows + inProgressRows + gameRows

    val allRows: List<SaveSyncRow>
        get() = listOfNotNull(devicesRow) + contentRows

    val focusedRow: SaveSyncRow?
        get() = allRows.find { it.key == focusedRowKey } ?: contentRows.firstOrNull() ?: allRows.firstOrNull()

    val focusedIndex: Int
        get() = focusedRow?.let { row -> allRows.indexOfFirst { it.key == row.key } }?.takeIf { it >= 0 } ?: 0

    val presentedGameId: Long?
        get() = focusedRow?.rowGameId ?: contentRows.firstOrNull()?.rowGameId

    private val SaveSyncRow.rowGameId: Long?
        get() = when (this) {
            is AttentionRow -> gameId
            is InProgressRow -> gameId
            is GameSaveRow -> gameId
            OtherDevicesRow -> null
        }

    val isEmpty: Boolean
        get() = attentionRows.isEmpty() && inProgressRows.isEmpty() && gameRows.isEmpty()
}

data class ThisDeviceCard(
    val deviceName: String? = null,
    val deviceIdShort: String? = null,
    val kind: DeviceKind = DeviceKind.UNKNOWN,
    val client: String? = null,
    val clientVersion: String? = null,
    val serverVersion: String? = null,
    val saveCount: Int = 0,
    val isConnected: Boolean = false
)

data class DeviceSummary(
    val deviceId: String?,
    val deviceName: String,
    val kind: DeviceKind,
    val client: String?,
    val clientVersion: String?,
    val saveCount: Int,
    val latestSyncAt: Instant?
)

sealed interface SaveSyncRow {
    val key: String
}

data object OtherDevicesRow : SaveSyncRow {
    override val key: String get() = "devices"
}

data class AttentionRow(
    val conflictId: Long,
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val channelName: String?,
    val channelDisplay: String,
    val localTime: Instant?,
    val serverTime: Instant?,
    val localDeviceName: String?,
    val serverDeviceName: String?,
    val isLocalNewer: Boolean
) : SaveSyncRow {
    override val key: String get() = "attention:$conflictId"
}

data class InProgressRow(
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val direction: SyncDirection,
    val progress: Float,
    val statusLabel: String
) : SaveSyncRow {
    override val key: String get() = "progress:$gameId:$direction"
}

data class SaveSlotEntry(
    val saveSyncId: Long,
    val channelName: String?,
    val channelDisplay: String,
    val syncStatus: String,
    val lastSyncedAt: Instant?,
    val localUpdatedAt: Instant?,
    val serverUpdatedAt: Instant?,
    val lastSyncDeviceName: String?,
    val isLastSyncThisDevice: Boolean,
    val isJustSynced: Boolean
) {
    val savedAt: Instant?
        get() = when (syncStatus) {
            SaveSyncEntity.STATUS_SYNCED, SaveSyncEntity.STATUS_SERVER_NEWER ->
                serverUpdatedAt ?: localUpdatedAt
            SaveSyncEntity.STATUS_LOCAL_NEWER, SaveSyncEntity.STATUS_PENDING_UPLOAD ->
                localUpdatedAt ?: serverUpdatedAt
            else -> listOfNotNull(serverUpdatedAt, localUpdatedAt).maxOrNull()
        }
}

/**
 * One game and every save slot synced for it. A slot in conflict is not listed here; it stands as
 * its own [AttentionRow] until it is resolved, then joins the rest.
 */
data class GameSaveRow(
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val slots: List<SaveSlotEntry>
) : SaveSyncRow {
    override val key: String get() = "game:$gameId"

    val lastSyncedAt: Instant? get() = slots.mapNotNull { it.lastSyncedAt }.maxOrNull()

    val isJustSynced: Boolean get() = slots.any { it.isJustSynced }
}
