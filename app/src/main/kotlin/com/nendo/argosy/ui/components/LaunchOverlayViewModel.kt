package com.nendo.argosy.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaunchOverlayViewModel @Inject constructor(
    private val tracker: LaunchProgressTracker
) : ViewModel() {

    val progress: StateFlow<LaunchProgress?> = tracker.progress

    private val _promptFocus = MutableStateFlow(0)
    val promptFocus: StateFlow<Int> = _promptFocus.asStateFlow()

    init {
        viewModelScope.launch {
            tracker.progress
                .map { it?.step as? LaunchStep.Prompt }
                .distinctUntilChanged()
                .collect { _promptFocus.value = 0 }
        }
    }

    fun answer(option: LaunchPromptOption) = tracker.answer(option)

    fun cancel(): Boolean = tracker.cancel()

    fun hostStopped() = tracker.hostStopped()

    private val prompt: LaunchStep.Prompt? get() = progress.value?.step as? LaunchStep.Prompt

    private fun movePromptFocus(delta: Int): InputResult {
        val step = prompt ?: return InputResult.HANDLED
        _promptFocus.update { (it + delta).coerceIn(0, step.options.size) }
        return InputResult.handled(SoundType.NAVIGATE)
    }

    val inputHandler: InputHandler = object : InputHandler {
        override fun onUp(): InputResult = movePromptFocus(-1)
        override fun onDown(): InputResult = movePromptFocus(1)

        override fun onConfirm(): InputResult {
            val step = prompt ?: return InputResult.HANDLED
            val option = step.options.getOrNull(_promptFocus.value)
            if (option != null) answer(option) else cancel()
            return InputResult.handled(SoundType.SELECT)
        }

        override fun onBack(): InputResult =
            if (cancel()) InputResult.handled(SoundType.CLOSE_MODAL) else InputResult.HANDLED

        override fun onLeft(): InputResult = InputResult.HANDLED
        override fun onRight(): InputResult = InputResult.HANDLED
        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
    }
}
