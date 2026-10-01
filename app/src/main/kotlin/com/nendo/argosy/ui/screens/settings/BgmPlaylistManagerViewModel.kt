package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.BgmPlaylistEntity
import com.nendo.argosy.domain.usecase.music.GetBgmTrackGameCoversUseCase
import com.nendo.argosy.ui.audio.BgmPlaylistCoordinator
import com.nendo.argosy.ui.components.ListReorder
import com.nendo.argosy.ui.components.ReorderStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BgmFolderSourceUi(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val trackCount: Int,
    val disabledCount: Int,
    val isMissing: Boolean
)

data class BgmPlaylistRowUi(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val isMissing: Boolean,
    val enabled: Boolean = true,
    val isFolderCovered: Boolean = false,
    val sourceFolderName: String? = null,
    val coverPath: String? = null
)

data class BgmPlaylistManagerState(
    val folderSources: List<BgmFolderSourceUi> = emptyList(),
    val entries: List<BgmPlaylistRowUi> = emptyList(),
    val focusedIndex: Int = 0,
    val reorder: ListReorder<BgmPlaylistRowUi>? = null
) {
    val isReordering: Boolean get() = reorder != null
    val heldTrackIndex: Int? get() = reorder?.heldIndex
    val focusCount: Int get() = folderSources.size + entries.size
    val isEmpty: Boolean get() = focusCount == 0
    val focusedSource: BgmFolderSourceUi? get() = folderSources.getOrNull(focusedIndex)
    val focusedEntry: BgmPlaylistRowUi? get() =
        if (focusedIndex >= folderSources.size) entries.getOrNull(focusedIndex - folderSources.size) else null
}

@HiltViewModel
class BgmPlaylistManagerViewModel @Inject constructor(
    private val coordinator: BgmPlaylistCoordinator,
    private val getBgmTrackGameCovers: GetBgmTrackGameCoversUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BgmPlaylistManagerState())
    val uiState: StateFlow<BgmPlaylistManagerState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.entries
                .map { rows -> rows.toGroups() }
                .flowOn(Dispatchers.IO)
                .collect { (sources, tracks) ->
                    _uiState.update { st ->
                        if (st.isReordering) st
                        else st.copy(
                            folderSources = sources,
                            entries = tracks,
                            focusedIndex = st.focusedIndex.coerceIn(
                                0,
                                (sources.size + tracks.size - 1).coerceAtLeast(0)
                            )
                        )
                    }
                }
        }
    }

    private suspend fun List<BgmPlaylistEntity>.toGroups(): Pair<List<BgmFolderSourceUi>, List<BgmPlaylistRowUi>> {
        val folders = filter { it.entryType == BgmPlaylistEntity.TYPE_FOLDER }
        val files = filter { it.entryType != BgmPlaylistEntity.TYPE_FOLDER }
        val folderNameById = folders.associate { it.id to it.displayName }
        val coverByGameFileId = getBgmTrackGameCovers(files.mapNotNull { it.gameFileId })
        val sourcedFiles = files.filter { it.sourceEntryId != null }
        val sourcedCounts = sourcedFiles.groupingBy { it.sourceEntryId }.eachCount()
        val disabledCounts = sourcedFiles.filter { !it.enabled }.groupingBy { it.sourceEntryId }.eachCount()
        val sources = folders.map { folder ->
            BgmFolderSourceUi(
                id = folder.id,
                displayName = folder.displayName,
                filePath = folder.filePath,
                trackCount = sourcedCounts[folder.id] ?: 0,
                disabledCount = disabledCounts[folder.id] ?: 0,
                isMissing = !File(folder.filePath).isDirectory
            )
        }
        val tracks = files.map { file ->
            val coveringFolder = folders.firstOrNull {
                file.filePath.startsWith(it.filePath.trimEnd('/') + "/")
            }
            BgmPlaylistRowUi(
                id = file.id,
                displayName = file.displayName,
                filePath = file.filePath,
                isMissing = !File(file.filePath).exists(),
                enabled = file.enabled,
                isFolderCovered = file.sourceEntryId != null || coveringFolder != null,
                sourceFolderName = file.sourceEntryId?.let { folderNameById[it] }
                    ?: coveringFolder?.displayName,
                coverPath = file.gameFileId?.let { coverByGameFileId[it] }
            )
        }
        return sources to tracks
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusedIndex = index.coerceIn(0, (it.focusCount - 1).coerceAtLeast(0))) }
    }

    fun moveFocus(delta: Int) {
        _uiState.update { st ->
            if (st.isEmpty) st
            else st.copy(focusedIndex = (st.focusedIndex + delta).mod(st.focusCount))
        }
    }

    fun lift() {
        _uiState.update { st ->
            if (st.isReordering) return@update st
            val reorder = ListReorder.lift(st.entries, st.focusedIndex - st.folderSources.size)
                ?: return@update st
            st.copy(reorder = reorder)
        }
    }

    fun liftAt(trackId: Long) {
        _uiState.update { st ->
            val index = st.entries.indexOfFirst { it.id == trackId }
            val reorder = ListReorder.liftOrRegrab(st.reorder, st.entries, index) ?: return@update st
            st.copy(reorder = reorder, focusedIndex = st.folderSources.size + reorder.heldIndex)
        }
    }

    fun moveHeld(delta: Int): Boolean {
        var moved = false
        _uiState.update { st ->
            val step = st.reorder?.moveBy(st.entries, delta) ?: return@update st
            moved = step.moved
            st.applyStep(step)
        }
        return moved
    }

    fun moveHeldTo(trackId: Long, trackIndex: Int) {
        _uiState.update { st ->
            val reorder = st.reorder ?: return@update st
            if (st.entries.getOrNull(reorder.heldIndex)?.id != trackId) return@update st
            st.applyStep(reorder.moveTo(st.entries, trackIndex))
        }
    }

    fun drop() {
        var committed: List<BgmPlaylistRowUi>? = null
        _uiState.update { st ->
            val reorder = st.reorder ?: return@update st
            committed = reorder.changedOrder(st.entries)
            st.copy(reorder = null)
        }
        val order = committed ?: return
        viewModelScope.launch { coordinator.reorder(order.map { it.id }) }
    }

    fun cancel() {
        _uiState.update { st ->
            val reorder = st.reorder ?: return@update st
            st.copy(
                reorder = null,
                entries = reorder.backup,
                focusedIndex = st.folderSources.size + reorder.originIndex
            )
        }
    }

    private fun BgmPlaylistManagerState.applyStep(step: ReorderStep<BgmPlaylistRowUi>): BgmPlaylistManagerState =
        copy(
            entries = step.items,
            reorder = step.reorder,
            focusedIndex = folderSources.size + step.reorder.heldIndex
        )

    fun removeSource(sourceIndex: Int) {
        val st = _uiState.value
        if (st.isReordering) return
        val source = st.folderSources.getOrNull(sourceIndex) ?: return
        viewModelScope.launch { coordinator.removeFolderSource(source.id) }
    }

    fun removeTrack(trackIndex: Int): Boolean {
        val st = _uiState.value
        if (st.isReordering) return false
        val row = st.entries.getOrNull(trackIndex) ?: return false
        if (row.isFolderCovered) return false
        viewModelScope.launch { coordinator.removeById(row.id) }
        return true
    }

    fun setTrackEnabled(trackIndex: Int, enabled: Boolean): Boolean {
        val st = _uiState.value
        if (st.isReordering) return false
        val row = st.entries.getOrNull(trackIndex) ?: return false
        if (row.enabled == enabled) return false
        viewModelScope.launch {
            when {
                enabled || !row.isFolderCovered -> coordinator.setTrackEnabled(row.id, enabled)
                else -> coordinator.removeOrDisableById(row.id)
            }
        }
        return true
    }

    fun toggleFocusedTrack(): Boolean {
        val st = _uiState.value
        val row = st.focusedEntry ?: return false
        return setTrackEnabled(st.focusedIndex - st.folderSources.size, !row.enabled)
    }

    fun removeFocused(): Boolean {
        val st = _uiState.value
        if (st.isReordering) return false
        if (st.focusedSource != null) {
            removeSource(st.focusedIndex)
            return true
        }
        return removeTrack(st.focusedIndex - st.folderSources.size)
    }
}
