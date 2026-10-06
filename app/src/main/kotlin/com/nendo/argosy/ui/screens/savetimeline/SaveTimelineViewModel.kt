package com.nendo.argosy.ui.screens.savetimeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelService
import com.nendo.argosy.data.sync.snapshot.SnapshotLibrary
import com.nendo.argosy.ui.common.savechannel.SaveChannelStateHolder
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewActions
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewDelegate
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewState
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val JUMP_STEP = 5

private data class LibraryStatus(val isLoading: Boolean, val loadFailed: Boolean)

private data class TimelineSources(
    val library: SnapshotLibrary?,
    val histories: Map<String, List<RomMSnapshot>>,
    val loads: Map<String, TimelineLaneLoad>,
    val status: LibraryStatus,
    val unavailable: Boolean
)

/**
 * Every save channel of one game as parallel lanes on a shared time axis. Data and channel
 * actions come from the save modal's [SaveChannelStateHolder], so the detail sheet, the channel
 * menu and their confirmations behave exactly as they do in the modal.
 */
@HiltViewModel
class SaveTimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val holder: SaveChannelStateHolder,
    private val delegate: SnapshotViewDelegate,
    private val service: SnapshotChannelService,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle["gameId"])

    private val _uiState = MutableStateFlow(SaveTimelineUiState(gameId = gameId))
    val uiState: StateFlow<SaveTimelineUiState> = _uiState.asStateFlow()

    val snapshotView: StateFlow<SnapshotViewState?> = holder.state
        .map { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, holder.state.value.snapshot)

    val viewActions = SnapshotViewActions(delegate, viewModelScope, ::publishSaveStatus)

    private val loads = MutableStateFlow<Map<String, TimelineLaneLoad>>(emptyMap())
    private val unavailable = MutableStateFlow(false)
    private var firstPagesJob: Job? = null
    private var initialFocusResolved = false

    init {
        viewModelScope.launch { loadHeader() }
        viewModelScope.launch { ensureLibrary() }
        viewModelScope.launch { watchLibrary() }
        viewModelScope.launch { watchSources() }
    }

    override fun onCleared() {
        delegate.returnToChannel(_uiState.value.focusedLane?.channelId)
        super.onCleared()
    }

    val isOverlayOpen: Boolean get() = holder.state.value.snapshot?.hasOverlay == true

    fun exit(onBack: () -> Unit) {
        delegate.returnToChannel(_uiState.value.focusedLane?.channelId)
        onBack()
    }

    fun moveAlong(delta: Int): Boolean {
        val state = _uiState.value
        val lane = state.focusedLane ?: return false
        if (lane.nodes.isEmpty()) return false
        val target = (state.focusPosition + delta).coerceIn(0, lane.nodes.lastIndex)
        if (target == state.focusPosition && followFork(state, delta)) return true
        _uiState.update { it.copy(focusPosition = target, recenterTick = it.recenterTick + 1) }
        loadOlderIfAtEdge()
        return target != state.focusPosition
    }

    private fun followFork(state: SaveTimelineUiState, delta: Int): Boolean {
        val (laneIndex, position) = SaveTimelineBuilder.forkStep(state, delta) ?: return false
        _uiState.update {
            it.copy(focusLane = laneIndex, focusPosition = position, recenterTick = it.recenterTick + 1)
        }
        loadOlderIfAtEdge()
        return true
    }

    fun moveLane(delta: Int): Boolean {
        val state = _uiState.value
        var target = state.focusLane + delta
        while (target in state.lanes.indices && state.lanes[target].nodes.isEmpty()) target += delta
        val lane = state.lanes.getOrNull(target)
        if (lane == null) {
            recenter()
            return false
        }
        val at = state.focusedNode?.createdAtMillis ?: Long.MAX_VALUE
        val position = SaveTimelineBuilder.closestPositionNewerOnTie(lane, at)
        _uiState.update {
            it.copy(focusLane = target, focusPosition = position, recenterTick = it.recenterTick + 1)
        }
        loadOlderIfAtEdge()
        return true
    }

    fun jump(direction: Int): Boolean = moveAlong(direction * JUMP_STEP)

    fun recenter() {
        _uiState.update { it.copy(recenterTick = it.recenterTick + 1) }
    }

    fun tapNode(lane: Int, position: Int) {
        if (isOverlayOpen) return
        if (_uiState.value.isFocused(lane, position)) {
            openFocusedDetail()
            return
        }
        focus(lane, position)
    }

    fun longPressNode(lane: Int, position: Int) {
        if (isOverlayOpen) return
        focus(lane, position)
        openFocusedChannelActions()
    }

    fun tapLane(lane: Int) {
        if (isOverlayOpen) return
        focusLane(lane)
    }

    fun longPressLane(lane: Int) {
        if (isOverlayOpen) return
        focusLane(lane)
        _uiState.value.lanes.getOrNull(lane)?.let { delegate.openChannelActions(it.channelId) }
    }

    fun openFocusedDetail() {
        val state = _uiState.value
        val lane = state.focusedLane ?: return
        val node = state.focusedNode ?: return
        delegate.openDetail(lane.channelId, node.card)
    }

    fun openFocusedChannelActions() {
        _uiState.value.focusedLane?.let { delegate.openChannelActions(it.channelId) }
    }

    fun overlayVertical(delta: Int) = delegate.moveVertical(delta)

    fun overlayHorizontal(delta: Int) = delegate.moveHorizontal(delta)

    fun overlayConfirm() = delegate.confirm(viewModelScope, ::publishSaveStatus)

    fun overlayBack(): Boolean = delegate.back()

    private fun focus(lane: Int, position: Int) {
        if (_uiState.value.lanes.getOrNull(lane)?.nodes?.getOrNull(position) == null) return
        _uiState.update { it.copy(focusLane = lane, focusPosition = position) }
        loadOlderIfAtEdge()
    }

    private fun focusLane(lane: Int) {
        val state = _uiState.value
        val target = state.lanes.getOrNull(lane) ?: return
        if (target.nodes.isEmpty()) {
            _uiState.update { it.copy(focusLane = lane, focusPosition = -1) }
            return
        }
        val at = state.focusedNode?.createdAtMillis ?: target.nodes.last().createdAtMillis
        _uiState.update { it.copy(focusLane = lane, focusPosition = SaveTimelineBuilder.closestPositionNewerOnTie(target, at)) }
        loadOlderIfAtEdge()
    }

    private fun loadOlderIfAtEdge() {
        val state = _uiState.value
        val lane = state.focusedLane ?: return
        val oldestSnapshot = lane.oldestSnapshotPosition
        if (oldestSnapshot < 0 || state.focusPosition > oldestSnapshot) return
        loadOlder(lane.channelId)
    }

    private fun loadOlder(channelId: String) {
        val load = loads.value[channelId] ?: return
        if (!load.hasMore || load.isLoading || load.isLoadingMore) return
        loads.update { it + (channelId to load.copy(isLoadingMore = true)) }
        viewModelScope.launch { fetchPage(channelId, more = true) }
    }

    private suspend fun loadHeader() {
        val game = gameRepository.getById(gameId) ?: return
        val platform = platformRepository.getById(game.platformId)
        _uiState.update { it.copy(header = SaveTimelineHeaderUi(title = game.title, platformName = platform?.name)) }
    }

    private suspend fun ensureLibrary() {
        if (holder.currentGameId == gameId && holder.state.value.snapshot != null) return
        holder.currentGameId = gameId
        if (!delegate.isAvailable(gameId)) {
            unavailable.value = true
            return
        }
        delegate.start(viewModelScope)
    }

    private suspend fun watchLibrary() {
        var fetchedFor: SnapshotLibrary? = null
        combine(holder.snapshotLibrary, holder.snapshotHistories) { library, histories -> library to histories.isEmpty() }
            .distinctUntilChanged()
            .collect { (library, historiesCleared) ->
                if (library == null) return@collect
                if (library != fetchedFor || historiesCleared) {
                    fetchedFor = library
                    fetchFirstPages(library)
                }
            }
    }

    private fun fetchFirstPages(library: SnapshotLibrary) {
        firstPagesJob?.cancel()
        val entries = library.mine + library.community
        loads.value = entries.associate { it.channel.id to TimelineLaneLoad(isLoading = it.channel.current != null) }
        firstPagesJob = viewModelScope.launch {
            entries.filter { it.channel.current != null }
                .map { entry -> async { fetchPage(entry.channel.id, more = false) } }
                .awaitAll()
        }
    }

    private suspend fun fetchPage(channelId: String, more: Boolean) {
        val before = if (more) holder.snapshotHistories.value[channelId]?.lastOrNull()?.id else null
        val page = service.history(channelId, before)
        if (page == null) {
            if (more) {
                notificationManager.showError(NotificationText.Res(R.string.save_channels_timeline_notice_history_failed))
            }
            loads.update { current ->
                current + (channelId to TimelineLaneLoad(
                    isLoading = false,
                    hasMore = more && current[channelId]?.hasMore == true,
                    failed = !more
                ))
            }
            return
        }
        holder.snapshotHistories.update { map ->
            map + (channelId to if (more) map[channelId].orEmpty() + page else page)
        }
        loads.update {
            it + (channelId to TimelineLaneLoad(isLoading = false, hasMore = page.size == SnapshotChannelService.HISTORY_PAGE))
        }
    }

    private suspend fun watchSources() {
        val status = holder.state
            .map { saveState -> LibraryStatus(saveState.snapshot?.isLoading != false, saveState.snapshot?.loadFailed == true) }
            .distinctUntilChanged()
        combine(holder.snapshotLibrary, holder.snapshotHistories, loads, status, unavailable) {
                library, histories, laneLoads, libraryStatus, isUnavailable ->
            TimelineSources(library, histories, laneLoads, libraryStatus, isUnavailable)
        }.collect { applySources(it) }
    }

    private fun applySources(sources: TimelineSources) {
        val library = sources.library
        if (library == null) {
            _uiState.update {
                it.copy(
                    isLoading = !sources.unavailable && sources.status.isLoading,
                    loadFailed = sources.unavailable || sources.status.loadFailed
                )
            }
            return
        }
        val layout = SaveTimelineBuilder.build(library, sources.histories, sources.loads, delegate.mapper)
        var firstFocus = false
        _uiState.update { state ->
            val (lane, position) = resolveFocus(state, layout.lanes)
            firstFocus = !initialFocusResolved && position >= 0
            state.copy(
                isLoading = false,
                loadFailed = false,
                lanes = layout.lanes,
                columns = layout.columns,
                forks = layout.forks,
                deviceChannel = layout.lanes.firstOrNull { it.tile.isDeviceChannel }?.tile?.label,
                focusLane = lane,
                focusPosition = position,
                recenterTick = if (firstFocus) state.recenterTick + 1 else state.recenterTick
            )
        }
        if (firstFocus) {
            initialFocusResolved = true
            loadOlderIfAtEdge()
        }
    }

    private fun resolveFocus(state: SaveTimelineUiState, lanes: List<SaveTimelineLaneUi>): Pair<Int, Int> {
        val previousLane = state.focusedLane
        val previousNode = state.focusedNode
        if (initialFocusResolved && previousLane != null) {
            val lane = lanes.indexOfFirst { it.channelId == previousLane.channelId }
            val nodes = lanes.getOrNull(lane)?.nodes.orEmpty()
            val byKey = nodes.indexOfFirst { it.card.key == previousNode?.card?.key }
            when {
                byKey >= 0 -> return lane to byKey
                lane >= 0 && nodes.isEmpty() && previousNode == null -> return lane to -1
                lane >= 0 && nodes.isNotEmpty() -> return lane to SaveTimelineBuilder.closestPositionNewerOnTie(
                    lanes[lane],
                    previousNode?.createdAtMillis ?: nodes.last().createdAtMillis
                )
            }
        }
        if (!initialFocusResolved && lanes.any { it.isLoading }) return -1 to -1
        return initialFocus(lanes)
    }

    private fun initialFocus(lanes: List<SaveTimelineLaneUi>): Pair<Int, Int> {
        val deviceLane = lanes.indexOfFirst { it.tile.isDeviceChannel }
        val current = lanes.getOrNull(deviceLane)?.nodes?.indexOfFirst { it.card.isCurrent } ?: -1
        if (current >= 0) return deviceLane to current
        val newest = lanes.withIndex()
            .flatMap { (laneIndex, lane) -> lane.nodes.withIndex().map { (position, node) -> Triple(laneIndex, position, node.column) } }
            .maxByOrNull { it.third }
            ?: return -1 to -1
        return newest.first to newest.second
    }

    private fun publishSaveStatus(event: SaveStatusEvent) {
        holder.saveStatusEvents.tryEmit(event)
    }
}
