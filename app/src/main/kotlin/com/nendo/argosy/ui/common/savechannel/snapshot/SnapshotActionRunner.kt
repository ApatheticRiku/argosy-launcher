package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotActionResult
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelEntry
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelService
import com.nendo.argosy.domain.usecase.savechannel.UseSnapshotChannelOnDeviceUseCase
import com.nendo.argosy.hardware.SaveScreenshotCapture
import com.nendo.argosy.ui.common.savechannel.SaveChannelStateHolder
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal fun SaveChannelStateHolder.updateSnapshot(transform: (SnapshotViewState) -> SnapshotViewState) {
    state.update { it.copy(snapshot = it.snapshot?.let(transform)) }
}

sealed class SnapshotPush(val done: NotificationText) {
    class Fork(val romFileId: Long, val snapshotId: Long, val label: String) :
        SnapshotPush(NotificationText.Res(R.string.save_channels_notice_forked))
    class CopyOver(val snapshotId: Long, val target: RomMChannel) :
        SnapshotPush(NotificationText.Res(R.string.save_channels_notice_copied))
    class MakeSnapshot(val channel: RomMChannel, val saveId: Long) :
        SnapshotPush(NotificationText.Res(R.string.save_channels_notice_made_snapshot))
}

/**
 * Server side of the snapshot channel view: loads the library and channel history into
 * [SaveChannelStateHolder], runs channel actions, and reports their results the way RomM's
 * channel panel does.
 */
class SnapshotActionRunner @Inject constructor(
    private val holder: SaveChannelStateHolder,
    private val service: SnapshotChannelService,
    private val useChannelOnDevice: UseSnapshotChannelOnDeviceUseCase,
    romMRepository: RomMRepository,
    saveScreenshots: SaveScreenshotCapture,
    private val notificationManager: NotificationManager
) {
    val mapper = SnapshotUiMapper(romMRepository::buildMediaUrlPublic) { snapshotId ->
        saveScreenshots.snapshotThumbFor(snapshotId)?.absolutePath
    }
    private var pending: SnapshotPush? = null

    suspend fun isAvailable(gameId: Long): Boolean =
        withContext(Dispatchers.IO) { runCatching { service.isAvailable(gameId) }.getOrDefault(false) }

    fun start(scope: CoroutineScope) {
        holder.snapshotHistories.value = emptyMap()
        holder.snapshotLibrary.value = null
        pending = null
        focusDeviceChannel = true
        holder.state.update { it.copy(snapshot = SnapshotViewState()) }
        scope.launch {
            service.cached(holder.currentGameId)?.let(::show)
            reload()
        }
    }

    private var focusDeviceChannel = false

    private fun show(library: com.nendo.argosy.data.sync.snapshot.SnapshotLibrary) {
        holder.snapshotLibrary.value = library
        holder.updateSnapshot { state ->
            val applied = mapper.applyLibrary(state, library)
            if (!focusDeviceChannel) return@updateSnapshot applied
            val index = applied.mine.indexOfFirst { it.channelId == library.deviceChannelId }
            if (index < 0) applied else applied.copy(stop = SnapshotStop.MineTiles, mineIndex = index)
        }
        if (library.deviceChannelId != null) focusDeviceChannel = false
    }

    fun clear() {
        holder.snapshotHistories.value = emptyMap()
        holder.snapshotLibrary.value = null
        pending = null
    }

    fun entryOf(channelId: String): SnapshotChannelEntry? {
        val library = holder.snapshotLibrary.value ?: return null
        return (library.mine + library.community).firstOrNull { it.channel.id == channelId }
    }

    fun ownChannels(): List<RomMChannel> {
        val library = holder.snapshotLibrary.value ?: return emptyList()
        return library.mine.map { it.channel }.filter { (it.romFileId ?: library.romFileId) == library.romFileId }
    }

    fun copyTargets(from: RomMChannel): List<RomMChannel> =
        holder.snapshotLibrary.value?.mine.orEmpty().map { it.channel }.filter {
            it.id != from.id && it.romFileId != null && it.romFileId == from.romFileId
        }

    fun romFileIdFor(channel: RomMChannel?): Long? = channel?.romFileId ?: holder.snapshotLibrary.value?.romFileId

    fun loadHistory(scope: CoroutineScope, channelId: String, more: Boolean) {
        scope.launch { fetchHistory(channelId, more) }
    }

    fun push(scope: CoroutineScope, push: SnapshotPush, approve: Boolean = false) {
        if (!beginBusy()) return
        scope.launch {
            val result = execute(push, approve)
            settle(result, push.done, push)
        }
    }

    fun approvePending(scope: CoroutineScope) {
        val retry = pending ?: return
        pending = null
        push(scope, retry, approve = true)
    }

    fun dropPending() {
        pending = null
    }

    fun newChannel(scope: CoroutineScope, label: String, fromBackupId: Long?) {
        val romFileId = holder.snapshotLibrary.value?.romFileId ?: return
        call(scope, NotificationText.Res(R.string.save_channels_notice_created)) {
            service.newChannel(romFileId, label, fromBackupId)
        }
    }

    fun rename(scope: CoroutineScope, channelId: String, label: String) =
        call(scope, NotificationText.Res(R.string.save_channels_notice_renamed)) { service.rename(channelId, label) }

    fun setShared(scope: CoroutineScope, channelId: String, shared: Boolean) =
        call(
            scope,
            NotificationText.Res(if (shared) R.string.save_channels_notice_shared else R.string.save_channels_notice_unshared)
        ) { service.setShared(channelId, shared) }

    fun delete(scope: CoroutineScope, channelId: String) {
        holder.updateSnapshot { state ->
            if (state.expanded?.channelId == channelId) SnapshotFocus.collapse(state) else state
        }
        call(scope, NotificationText.Res(R.string.save_channels_notice_deleted)) { service.delete(channelId) }
    }

    fun setPinned(scope: CoroutineScope, channelId: String, snapshotId: Long, pinned: Boolean) {
        if (!beginBusy()) return
        scope.launch {
            val result = service.setPinned(snapshotId, pinned)
            holder.updateSnapshot { it.copy(isBusy = false) }
            if (result != SnapshotActionResult.Done) {
                report(result, retry = null)
                return@launch
            }
            val entry = entryOf(channelId) ?: return@launch
            val history = holder.snapshotHistories.value[channelId].orEmpty().map {
                if (it.id == snapshotId) it.copy(isPinned = pinned) else it
            }
            holder.snapshotHistories.update { it + (channelId to history) }
            holder.updateSnapshot { state ->
                val cards = mapper.cards(entry, history)
                state.copy(
                    expanded = state.expanded?.takeIf { it.channelId == channelId }?.copy(cards = cards) ?: state.expanded,
                    detail = state.detail?.takeIf { it.card.snapshotId == snapshotId }?.let { detail ->
                        detail.copy(
                            card = detail.card.copy(isPinned = pinned),
                            actions = detail.actions.map { action ->
                                when (action) {
                                    SnapshotDetailAction.PIN, SnapshotDetailAction.UNPIN ->
                                        if (pinned) SnapshotDetailAction.UNPIN else SnapshotDetailAction.PIN
                                    else -> action
                                }
                            }
                        )
                    } ?: state.detail
                )
            }
        }
    }

    fun useOnDevice(
        scope: CoroutineScope,
        channelId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        snapshotId: Long? = null
    ) {
        val channel = entryOf(channelId)?.channel ?: return
        val emulatorId = holder.state.value.emulatorId ?: return
        val romFileId = romFileIdFor(channel) ?: return
        if (!beginBusy()) return
        scope.launch {
            val result = useChannelOnDevice(holder.currentGameId, emulatorId, romFileId, channel, snapshotId)
            if (result == SnapshotActionResult.Done) {
                val argosyChannel = service.argosyChannelOf(channel)
                holder.state.update { it.copy(activeChannel = argosyChannel) }
                onSaveStatusChanged(SaveStatusEvent(channelName = argosyChannel, timestamp = null))
            }
            settle(
                result,
                NotificationText.Res(R.string.save_channels_notice_used_on_device, listOf(channel.label)),
                retry = null
            )
        }
    }

    private fun call(
        scope: CoroutineScope,
        done: NotificationText,
        block: suspend () -> SnapshotActionResult
    ) {
        if (!beginBusy()) return
        scope.launch { settle(block(), done, retry = null) }
    }

    private fun beginBusy(): Boolean {
        val snapshot = holder.state.value.snapshot ?: return false
        if (snapshot.isBusy) return false
        holder.updateSnapshot { it.copy(isBusy = true) }
        return true
    }

    private suspend fun execute(push: SnapshotPush, approve: Boolean): SnapshotActionResult =
        when (push) {
            is SnapshotPush.Fork -> service.fork(push.romFileId, push.snapshotId, push.label, approve)
            is SnapshotPush.CopyOver -> service.copyOver(push.snapshotId, push.target, approve)
            is SnapshotPush.MakeSnapshot -> service.makeSnapshot(push.channel, push.saveId, approve)
        }

    private suspend fun settle(result: SnapshotActionResult, done: NotificationText, retry: SnapshotPush?) {
        holder.updateSnapshot { it.copy(isBusy = false) }
        when (result) {
            SnapshotActionResult.Done -> {
                notificationManager.showSuccess(done)
                holder.updateSnapshot {
                    it.copy(detail = null, channelMenu = null, copyPicker = null, labelEntry = null, confirm = null)
                }
                reload()
            }
            SnapshotActionResult.Stale -> {
                notificationManager.show(
                    title = NotificationText.Res(R.string.save_channels_notice_stale),
                    type = NotificationType.WARNING,
                    duration = NotificationDuration.MEDIUM
                )
                reload()
            }
            else -> report(result, retry)
        }
    }

    private fun report(result: SnapshotActionResult, retry: SnapshotPush?) {
        when (result) {
            SnapshotActionResult.HardcoreDowngrade ->
                if (retry != null) {
                    pending = retry
                    holder.updateSnapshot { it.copy(confirm = SnapshotConfirmUi.HardcoreDowngrade) }
                } else {
                    notificationManager.showError(NotificationText.Res(R.string.save_channels_notice_hardcore_kept))
                }
            SnapshotActionResult.Offline ->
                notificationManager.showError(NotificationText.Res(R.string.save_channels_notice_offline))
            is SnapshotActionResult.Failed ->
                notificationManager.showError(NotificationText.Res(result.failure.messageRes))
            SnapshotActionResult.Done, SnapshotActionResult.Stale -> Unit
        }
    }

    private suspend fun reload() {
        val library = service.load(holder.currentGameId)
        if (library == null) {
            holder.updateSnapshot { state ->
                if (state.mine.isEmpty() && state.community.isEmpty()) state.copy(isLoading = false, loadFailed = true)
                else state.copy(isLoading = false)
            }
            return
        }
        show(library)
        holder.state.value.snapshot?.expanded?.let { fetchHistory(it.channelId, more = false) }
    }

    private suspend fun fetchHistory(channelId: String, more: Boolean) {
        val entry = entryOf(channelId) ?: return
        if (entry.channel.current == null) {
            holder.updateSnapshot { state -> state.withCards(channelId, mapper.cards(entry, emptyList()), hasMore = false) }
            return
        }
        val loaded = holder.snapshotHistories.value[channelId].orEmpty()
        if (more) {
            holder.updateSnapshot { state ->
                state.copy(expanded = state.expanded?.takeIf { it.channelId == channelId }?.copy(isLoadingMore = true) ?: state.expanded)
            }
        }
        val page = service.history(channelId, if (more) loaded.lastOrNull()?.id else null)
        if (page == null) {
            notificationManager.showError(NotificationText.Res(R.string.save_channels_notice_history_failed))
            holder.updateSnapshot { state ->
                state.copy(
                    expanded = state.expanded?.takeIf { it.channelId == channelId }
                        ?.copy(isLoading = false, isLoadingMore = false) ?: state.expanded
                )
            }
            return
        }
        val history = if (more) loaded + page else page
        holder.snapshotHistories.update { it + (channelId to history) }
        holder.updateSnapshot { state ->
            state.withCards(channelId, mapper.cards(entry, history), hasMore = page.size == SnapshotChannelService.HISTORY_PAGE)
        }
    }

    private fun SnapshotViewState.withCards(channelId: String, cards: List<SnapshotCardUi>, hasMore: Boolean): SnapshotViewState {
        val open = expanded?.takeIf { it.channelId == channelId } ?: return this
        val stopCount = cards.size + if (hasMore) 1 else 0
        return copy(
            expanded = open.copy(
                cards = cards,
                isLoading = false,
                isLoadingMore = false,
                hasMore = hasMore,
                focusIndex = open.focusIndex.coerceIn(0, (stopCount - 1).coerceAtLeast(0))
            )
        )
    }
}
