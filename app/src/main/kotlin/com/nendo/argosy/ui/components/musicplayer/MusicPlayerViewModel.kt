package com.nendo.argosy.ui.components.musicplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.domain.model.MusicPlaylistEntry
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicServerStatus
import com.nendo.argosy.domain.usecase.music.GetMusicPlaylistsUseCase
import com.nendo.argosy.domain.usecase.music.ResolveMusicQueueUseCase
import com.nendo.argosy.domain.usecase.music.SearchSoundtrackGamesUseCase
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MusicPlayerVM"
private const val SOUNDTRACK_PAGE_SIZE = 50
private const val PREFETCH_THRESHOLD = 8

@OptIn(FlowPreview::class)
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val controlsPreferences: ControlsPreferencesRepository,
    private val getMusicPlaylists: GetMusicPlaylistsUseCase,
    private val searchSoundtrackGames: SearchSoundtrackGamesUseCase,
    private val resolveMusicQueue: ResolveMusicQueueUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    private val searchInput = MutableStateFlow("")
    private var wrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP
    private var playlistCatalog: List<MusicPlaylistEntry> = emptyList()
    private var loadJob: Job? = null
    private var selectJob: Job? = null

    init {
        viewModelScope.launch {
            ambientAudioManager.playback.collect { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
        }
        viewModelScope.launch {
            controlsPreferences.preferences.collect { prefs ->
                wrapMode = prefs.menuWrapMode
                _uiState.update { it.copy(launcherEnabled = prefs.ambientAudioEnabled) }
            }
        }
        viewModelScope.launch {
            searchInput
                .drop(1)
                .debounce(ComponentDefaults.MusicPlayer.searchDebounceMs.toLong())
                .distinctUntilChanged()
                .collectLatest { onQueryChanged() }
        }
    }

    fun samplePosition() {
        val position = ambientAudioManager.positionSnapshot()
        _uiState.update {
            it.copy(
                positionMs = position?.positionMs ?: 0L,
                durationMs = position?.durationMs ?: 0L
            )
        }
    }

    fun onPageHidden() {
        if (_uiState.value.browse != null) closeBrowse()
    }

    fun moveRow(delta: Int): Boolean {
        val rows = MusicPlayerRow.entries
        val current = _uiState.value.focusedRow.ordinal
        val next = InputDispatcher.computeWrappedIndex(current, delta, rows.lastIndex, wrapMode)
        if (next == current) return false
        _uiState.update { it.copy(focusedRow = rows[next]) }
        return true
    }

    fun moveTransport(delta: Int) {
        val buttons = MusicTransportButton.entries
        _uiState.update {
            it.copy(transportButton = buttons[(it.transportButton.ordinal + delta).mod(buttons.size)])
        }
    }

    fun focusRow(row: MusicPlayerRow) {
        _uiState.update { it.copy(focusedRow = row) }
    }

    fun focusTransport(button: MusicTransportButton) {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.TRANSPORT, transportButton = button) }
    }

    fun confirmRow() {
        val state = _uiState.value
        when (state.focusedRow) {
            MusicPlayerRow.TRANSPORT -> activateTransport(state.transportButton)
            MusicPlayerRow.PLAYLISTS -> openBrowse(MusicBrowseKind.PLAYLISTS)
            MusicPlayerRow.SOUNDTRACKS -> openBrowse(MusicBrowseKind.SOUNDTRACKS)
            MusicPlayerRow.LAUNCHER_TOGGLE -> toggleLauncherMusic()
        }
    }

    fun activateTransport(button: MusicTransportButton) {
        when (button) {
            MusicTransportButton.PREVIOUS -> skipPrevious()
            MusicTransportButton.PLAY_PAUSE -> togglePlayPause()
            MusicTransportButton.NEXT -> skipNext()
            MusicTransportButton.SHUFFLE -> toggleShuffle()
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isAudible) {
            ambientAudioManager.pause()
            return
        }
        viewModelScope.launch {
            ensureEnabled()
            ambientAudioManager.play()
        }
    }

    fun skipNext() {
        if (!_uiState.value.launcherEnabled) return
        ambientAudioManager.skipNext()
    }

    fun skipPrevious() {
        if (!_uiState.value.launcherEnabled) return
        ambientAudioManager.skipPrevious()
    }

    fun toggleShuffle() {
        val next = !_uiState.value.playback.shuffle
        ambientAudioManager.setShuffle(next)
        viewModelScope.launch { controlsPreferences.setAmbientAudioShuffle(next) }
    }

    fun toggleLauncherMusic(): Boolean = setLauncherMusic(!_uiState.value.launcherEnabled)

    fun setLauncherMusic(enabled: Boolean): Boolean {
        if (enabled == _uiState.value.launcherEnabled) return enabled
        viewModelScope.launch { controlsPreferences.setAmbientAudioEnabled(enabled) }
        return enabled
    }

    private suspend fun ensureEnabled() {
        if (controlsPreferences.preferences.first().ambientAudioEnabled) return
        controlsPreferences.setAmbientAudioEnabled(true)
        ambientAudioManager.setEnabled(true)
    }

    fun openBrowse(kind: MusicBrowseKind) {
        loadJob?.cancel()
        selectJob?.cancel()
        searchInput.value = ""
        _uiState.update { it.copy(browse = MusicBrowseUi(kind = kind)) }
        loadBrowse(reset = true)
    }

    fun closeBrowse() {
        selectJob?.cancel()
        clearBrowse()
    }

    private fun clearBrowse() {
        loadJob?.cancel()
        searchInput.value = ""
        _uiState.update { it.copy(browse = null) }
    }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed: ${e.message}")
        null
    }

    fun updateQuery(query: String) {
        _uiState.update { state -> state.copy(browse = state.browse?.copy(query = query)) }
        searchInput.value = query
    }

    fun focusSearch() {
        _uiState.update { state ->
            state.copy(
                browse = state.browse?.copy(
                    focusIndex = MusicBrowseUi.SEARCH_FOCUS_INDEX,
                    showKeyboard = true
                )
            )
        }
    }

    fun setBrowseFocus(index: Int) {
        _uiState.update { state ->
            val browse = state.browse ?: return@update state
            state.copy(
                browse = browse.copy(
                    focusIndex = index.coerceIn(MusicBrowseUi.SEARCH_FOCUS_INDEX, browse.itemCount - 1),
                    showKeyboard = false
                )
            )
        }
    }

    fun moveBrowseFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            val browse = state.browse ?: return@update state
            val next = (browse.focusIndex + delta)
                .coerceIn(MusicBrowseUi.SEARCH_FOCUS_INDEX, browse.itemCount - 1)
            moved = next != browse.focusIndex
            state.copy(browse = browse.copy(focusIndex = next, showKeyboard = false))
        }
        maybeLoadMore()
        return moved
    }

    fun confirmBrowse(index: Int? = null) {
        val browse = _uiState.value.browse ?: return
        val target = index ?: browse.focusIndex
        when {
            target == MusicBrowseUi.SEARCH_FOCUS_INDEX -> focusSearch()
            browse.status == MusicBrowseStatus.FAILED -> loadBrowse(reset = true)
            else -> browse.rows.getOrNull(target)?.let { select(it.selection) }
        }
    }

    fun onListEndApproached() = maybeLoadMore(force = true)

    val needsRommSignIn: Boolean
        get() = _uiState.value.browse?.notice == MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS

    private fun maybeLoadMore(force: Boolean = false) {
        val browse = _uiState.value.browse ?: return
        if (browse.kind != MusicBrowseKind.SOUNDTRACKS || !browse.hasMore || browse.isLoadingMore) return
        if (!force && browse.focusIndex < browse.rows.size - PREFETCH_THRESHOLD) return
        loadBrowse(reset = false)
    }

    private fun select(selection: MusicSelection) {
        if (selection is MusicSelection.Launcher) {
            selectJob?.cancel()
            selectJob = viewModelScope.launch {
                ensureEnabled()
                ambientAudioManager.activateLauncher()
                clearBrowse()
            }
            return
        }
        if (_uiState.value.browse?.pendingSelectionId != null) return
        selectJob = viewModelScope.launch {
            updateBrowse { it.copy(pendingSelectionId = selection.id, notice = null) }
            val tracks = attempt("Queue resolve for ${selection.id}") { resolveMusicQueue(selection) }
                .orEmpty()
            if (tracks.isEmpty()) {
                updateBrowse {
                    it.copy(pendingSelectionId = null, notice = MusicBrowseNotice.NO_PLAYABLE_TRACKS)
                }
                return@launch
            }
            ensureEnabled()
            ambientAudioManager.playSelection(selection.id, selection.label(), tracks)
            clearBrowse()
        }
    }

    private fun onQueryChanged() {
        val browse = _uiState.value.browse ?: return
        when (browse.kind) {
            MusicBrowseKind.PLAYLISTS -> applyPlaylistFilter()
            MusicBrowseKind.SOUNDTRACKS -> loadBrowse(reset = true)
        }
    }

    private fun loadBrowse(reset: Boolean) {
        val browse = _uiState.value.browse ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateBrowse {
                if (reset) {
                    it.copy(status = MusicBrowseStatus.LOADING, rows = emptyList(), hasMore = false, notice = null)
                } else {
                    it.copy(isLoadingMore = true)
                }
            }
            when (browse.kind) {
                MusicBrowseKind.PLAYLISTS -> loadPlaylists()
                MusicBrowseKind.SOUNDTRACKS -> loadSoundtracks(reset)
            }
        }
    }

    private suspend fun loadPlaylists() {
        val catalog = attempt("Playlist load") { getMusicPlaylists() }
        if (catalog == null) {
            updateBrowse { it.copy(status = MusicBrowseStatus.FAILED, focusIndex = 0) }
            return
        }
        playlistCatalog = catalog.entries
        updateBrowse { it.copy(notice = playlistNotice(catalog.serverStatus)) }
        applyPlaylistFilter()
    }

    private fun applyPlaylistFilter() {
        val query = _uiState.value.browse?.query?.trim().orEmpty()
        val rows = playlistCatalog
            .filter { entry ->
                query.isEmpty() || entry.selection is MusicSelection.Launcher ||
                    (entry.selection as? MusicSelection.ServerPlaylist)?.name
                        ?.contains(query, ignoreCase = true) == true
            }
            .map { it.toRow() }
        updateBrowse { browse ->
            browse.copy(
                rows = rows,
                status = MusicBrowseStatus.READY,
                focusIndex = settledFocus(browse, rows.size)
            )
        }
    }

    private suspend fun loadSoundtracks(reset: Boolean) {
        val current = _uiState.value.browse ?: return
        val offset = if (reset) 0 else current.rows.size
        val page = attempt("Soundtrack load") {
            searchSoundtrackGames(current.query, offset, SOUNDTRACK_PAGE_SIZE)
        }
        if (page == null) {
            updateBrowse {
                if (reset) {
                    it.copy(status = MusicBrowseStatus.FAILED, focusIndex = 0, isLoadingMore = false)
                } else {
                    it.copy(isLoadingMore = false, hasMore = false)
                }
            }
            return
        }
        val newRows = page.entries.map { entry ->
            MusicBrowseRowUi(
                selection = entry.selection,
                title = entry.selection.title,
                ownerName = null,
                platformName = entry.platformName,
                coverPath = entry.coverPath,
                trackCount = entry.trackCount
            )
        }
        updateBrowse { browse ->
            val merged = if (reset) newRows else (browse.rows + newRows).distinctBy { it.key }
            browse.copy(
                rows = merged,
                status = MusicBrowseStatus.READY,
                hasMore = page.hasMore,
                isLoadingMore = false,
                notice = if (page.fromServer) null else soundtrackNotice(page.serverStatus),
                focusIndex = if (reset) settledFocus(browse, merged.size) else browse.focusIndex
            )
        }
    }

    private fun settledFocus(browse: MusicBrowseUi, count: Int): Int = when {
        browse.isSearchFocused -> MusicBrowseUi.SEARCH_FOCUS_INDEX
        count == 0 -> MusicBrowseUi.SEARCH_FOCUS_INDEX
        else -> browse.focusIndex.coerceIn(0, count - 1)
    }

    private fun updateBrowse(transform: (MusicBrowseUi) -> MusicBrowseUi) {
        _uiState.update { state -> state.copy(browse = state.browse?.let(transform)) }
    }

    private fun playlistNotice(status: MusicServerStatus): MusicBrowseNotice? = when (status) {
        MusicServerStatus.OFFLINE -> MusicBrowseNotice.OFFLINE
        MusicServerStatus.FAILED -> MusicBrowseNotice.SERVER_FAILED
        MusicServerStatus.UNAUTHORIZED -> MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS
        MusicServerStatus.AVAILABLE,
        MusicServerStatus.UNSUPPORTED -> null
    }

    private fun soundtrackNotice(status: MusicServerStatus): MusicBrowseNotice? = when (status) {
        MusicServerStatus.OFFLINE -> MusicBrowseNotice.OFFLINE
        MusicServerStatus.FAILED -> MusicBrowseNotice.SERVER_FAILED
        MusicServerStatus.UNSUPPORTED,
        MusicServerStatus.UNAUTHORIZED -> MusicBrowseNotice.LOCAL_ONLY
        MusicServerStatus.AVAILABLE -> null
    }

    private fun MusicPlaylistEntry.toRow(): MusicBrowseRowUi = MusicBrowseRowUi(
        selection = selection,
        title = (selection as? MusicSelection.ServerPlaylist)?.name,
        ownerName = ownerName,
        platformName = null,
        coverPath = null,
        trackCount = trackCount
    )

    private fun MusicSelection.label(): String? = when (this) {
        MusicSelection.Launcher -> null
        is MusicSelection.ServerPlaylist -> name
        is MusicSelection.GameSoundtrack -> title
    }
}
