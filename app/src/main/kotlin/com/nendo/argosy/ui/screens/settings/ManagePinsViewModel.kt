package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.ReorderPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
import com.nendo.argosy.ui.components.ListReorder
import com.nendo.argosy.ui.components.ReorderStep
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagePinsUiState(
    val pins: List<PinnedCollection> = emptyList(),
    val focusedIndex: Int = 0,
    val reorder: ListReorder<PinnedCollection>? = null,
    val isLoading: Boolean = true
) {
    val focusedPin: PinnedCollection?
        get() = pins.getOrNull(focusedIndex)

    val isReorderMode: Boolean
        get() = reorder != null
}

@HiltViewModel
class ManagePinsViewModel @Inject constructor(
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase,
    private val reorderPinnedCollectionsUseCase: ReorderPinnedCollectionsUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase
) : ViewModel() {

    private val _localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val focusedIndex: Int = 0,
        val reorder: ListReorder<PinnedCollection>? = null,
        val localPins: List<PinnedCollection>? = null
    )

    val uiState: StateFlow<ManagePinsUiState> = combine(
        getPinnedCollectionsUseCase(),
        _localState
    ) { dbPins, localState ->
        val pins = localState.localPins ?: dbPins
        ManagePinsUiState(
            pins = pins,
            focusedIndex = localState.focusedIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0)),
            reorder = localState.reorder,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManagePinsUiState()
    )

    fun moveFocus(delta: Int): Boolean {
        val pins = uiState.value.pins
        if (pins.isEmpty()) return false
        var moved = false
        _localState.update { current ->
            val newIndex = (current.focusedIndex + delta).coerceIn(0, pins.size - 1)
            moved = newIndex != current.focusedIndex
            if (moved) current.copy(focusedIndex = newIndex) else current
        }
        return moved
    }

    fun setFocusIndex(index: Int) {
        val pins = uiState.value.pins
        if (index !in pins.indices) return
        _localState.update { if (it.reorder != null) it else it.copy(focusedIndex = index) }
    }

    fun lift() {
        val state = uiState.value
        _localState.update { current ->
            if (current.reorder != null) return@update current
            val reorder = ListReorder.lift(state.pins, state.focusedIndex) ?: return@update current
            current.copy(reorder = reorder, localPins = state.pins, focusedIndex = reorder.heldIndex)
        }
    }

    fun liftAt(key: Any) {
        val state = uiState.value
        _localState.update { current ->
            val pins = current.localPins ?: state.pins
            val index = pins.indexOfFirst { it.id == key }
            val reorder = ListReorder.liftOrRegrab(current.reorder, pins, index) ?: return@update current
            current.copy(reorder = reorder, localPins = pins, focusedIndex = reorder.heldIndex)
        }
    }

    fun moveHeld(delta: Int): Boolean {
        var moved = false
        _localState.update { current ->
            val reorder = current.reorder ?: return@update current
            val pins = current.localPins ?: return@update current
            val step = reorder.moveBy(pins, delta)
            moved = step.moved
            current.applyStep(step)
        }
        return moved
    }

    fun moveHeldTo(key: Any, targetIndex: Int) {
        _localState.update { current ->
            val reorder = current.reorder ?: return@update current
            val pins = current.localPins ?: return@update current
            if (pins.getOrNull(reorder.heldIndex)?.id != key) return@update current
            current.applyStep(reorder.moveTo(pins, targetIndex))
        }
    }

    fun drop() {
        val current = _localState.value
        val reorder = current.reorder ?: return
        val pins = current.localPins ?: return
        val committed = reorder.changedOrder(pins)
        if (committed == null) {
            _localState.update { it.copy(reorder = null, localPins = null) }
            return
        }
        _localState.update { it.copy(reorder = null) }
        viewModelScope.launch {
            reorderPinnedCollectionsUseCase(committed)
            _localState.update { if (it.reorder == null) it.copy(localPins = null) else it }
        }
    }

    fun cancel() {
        _localState.update { current ->
            val reorder = current.reorder ?: return@update current
            current.copy(reorder = null, localPins = null, focusedIndex = reorder.originIndex)
        }
    }

    fun unpinFocused() {
        val pin = uiState.value.focusedPin ?: return
        viewModelScope.launch {
            when (pin) {
                is PinnedCollection.Regular -> unpinCollectionUseCase.unpinById(pin.id)
                is PinnedCollection.Virtual -> unpinCollectionUseCase.unpinById(pin.id)
            }
        }
    }

    private fun LocalState.applyStep(step: ReorderStep<PinnedCollection>): LocalState =
        copy(localPins = step.items, reorder = step.reorder, focusedIndex = step.reorder.heldIndex)

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = move(-1)

        override fun onDown(): InputResult = move(1)

        private fun move(delta: Int): InputResult {
            val moved = if (uiState.value.isReorderMode) moveHeld(delta) else moveFocus(delta)
            return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onConfirm(): InputResult {
            if (!uiState.value.isReorderMode) return InputResult.handled(SoundType.SILENT)
            drop()
            return InputResult.handled(SoundType.SELECT)
        }

        override fun onBack(): InputResult {
            if (uiState.value.isReorderMode) {
                cancel()
                return InputResult.handled(SoundType.BACK)
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = uiState.value
            when {
                state.isReorderMode -> drop()
                state.pins.isEmpty() -> return InputResult.handled(SoundType.SILENT)
                else -> lift()
            }
            return InputResult.handled(SoundType.SELECT)
        }

        override fun onContextMenu(): InputResult {
            val state = uiState.value
            if (state.isReorderMode || state.focusedPin == null) return InputResult.handled(SoundType.SILENT)
            unpinFocused()
            return InputResult.HANDLED
        }
    }
}
