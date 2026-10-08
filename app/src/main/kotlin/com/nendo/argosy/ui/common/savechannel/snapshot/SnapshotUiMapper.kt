package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotDevice
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelEntry
import com.nendo.argosy.data.sync.snapshot.SnapshotLibrary

class SnapshotUiMapper(
    private val mediaUrl: (String?) -> String?,
    private val localThumb: (Long) -> String? = { null }
) {

    private fun snapshotThumb(snapshot: RomMSnapshot?, serverPath: String?): String? =
        snapshot?.id?.let(localThumb) ?: mediaUrl(serverPath)

    fun tile(entry: SnapshotChannelEntry, deviceChannelId: String?): SnapshotTileUi {
        val channel = entry.channel
        val current = channel.current
        val newestOlder = entry.olderClientSaves.firstOrNull()
        return SnapshotTileUi(
            channelId = channel.id,
            label = channel.label,
            thumbnailUrl = snapshotThumb(
                current,
                current?.thumbnail?.downloadPath
                    ?: current?.save?.screenshot?.downloadPath
                    ?: newestOlder?.screenshot?.downloadPath
            ),
            isOwn = channel.isOwn,
            isHardcore = channel.isHardcore,
            isShared = channel.isPublic,
            ownerName = channel.ownerUsername,
            hasOnlyOlderSaves = current == null && entry.olderClientSaves.isNotEmpty(),
            isDeviceChannel = channel.id == deviceChannelId,
            savedAt = current?.createdAt ?: newestOlder?.updatedAt,
            device = current?.let { device(it.device) }
        )
    }

    fun backup(save: RomMSave): SnapshotBackupUi =
        SnapshotBackupUi(
            saveId = save.id,
            fileName = save.fileName,
            savedAt = save.updatedAt,
            thumbnailUrl = mediaUrl(save.screenshot?.downloadPath)
        )

    fun snapshotCard(snapshot: RomMSnapshot, channel: RomMChannel): SnapshotCardUi =
        SnapshotCardUi(
            key = "s-${snapshot.id}",
            snapshotId = snapshot.id,
            saveId = null,
            savedAt = snapshot.createdAt,
            device = device(snapshot.device),
            fileName = snapshot.save?.fileName,
            thumbnailUrl = snapshotThumb(snapshot, snapshot.thumbnail?.downloadPath ?: snapshot.save?.screenshot?.downloadPath),
            isCurrent = snapshot.id == channel.currentSnapshotId,
            isBranch = snapshot.isBranch,
            isPinned = snapshot.isPinned,
            isHardcore = snapshot.isHardcore,
            isArchival = snapshot.isArchival,
            isPublic = snapshot.isPublic
        )

    fun olderSaveCard(save: RomMSave): SnapshotCardUi =
        SnapshotCardUi(
            key = "l-${save.id}",
            snapshotId = null,
            saveId = save.id,
            savedAt = save.updatedAt,
            device = null,
            fileName = save.fileName,
            thumbnailUrl = mediaUrl(save.screenshot?.downloadPath),
            isCurrent = false,
            isBranch = false,
            isPinned = false,
            isHardcore = false
        )

    fun cards(entry: SnapshotChannelEntry, history: List<RomMSnapshot>): List<SnapshotCardUi> =
        history.map { snapshotCard(it, entry.channel) } + entry.olderClientSaves.map(::olderSaveCard)

    fun states(snapshot: RomMSnapshot): List<SnapshotCoreStatesUi> =
        snapshot.states.entries
            .sortedBy { it.key }
            .map { (core, slots) -> SnapshotCoreStatesUi(core, slots.keys.toList()) }

    fun applyLibrary(state: SnapshotViewState, library: SnapshotLibrary): SnapshotViewState {
        val mine = library.mine.map { tile(it, library.deviceChannelId) }
        val community = library.community.map { tile(it, library.deviceChannelId) }
        val expanded = state.expanded?.takeIf { open ->
            (mine + community).any { it.channelId == open.channelId }
        }
        val next = state.copy(
            isLoading = false,
            loadFailed = false,
            canCreateChannel = true,
            mine = mine,
            backups = library.backups.map(::backup),
            community = community,
            expanded = expanded,
            mineIndex = state.mineIndex.coerceIn(0, (mine.size - 1).coerceAtLeast(0)),
            communityIndex = state.communityIndex.coerceIn(0, (community.size - 1).coerceAtLeast(0))
        )
        return next.copy(stop = SnapshotFocus.clampStop(next, state.stop))
    }

    private fun device(device: RomMSnapshotDevice?): SnapshotDeviceUi {
        if (device == null) return SnapshotDeviceUi.Unknown
        if (!device.isOwn) return SnapshotDeviceUi.OtherUser
        return (device.name ?: device.client)?.let { SnapshotDeviceUi.Named(it) } ?: SnapshotDeviceUi.Unknown
    }
}
