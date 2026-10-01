package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputDispatcher.Companion.currentIsRepeat
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem
import com.nendo.argosy.ui.screens.settings.sections.createEmulatorsLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.emulatorsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.emulatorsSections

internal class EmulatorsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onPrevSection(): InputResult = jumpSection(-1)
    override fun onNextSection(): InputResult = jumpSection(1)

    private fun jumpSection(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val info = createEmulatorsLayoutInfo(state.emulators.platforms)
        val sections = emulatorsSections(info)
        val currentFocus = state.focusedIndex

        val target = if (direction > 0) {
            sections.firstOrNull { it.focusStartIndex > currentFocus }
        } else {
            sections.lastOrNull { it.focusStartIndex < currentFocus }
        }

        if (target != null) {
            viewModel.setFocusIndex(target.focusStartIndex)
            return InputResult.HANDLED
        }
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult = handleLeftRight(-1)
    override fun onRight(): InputResult = handleLeftRight(1)

    private fun handleLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val info = createEmulatorsLayoutInfo(state.emulators.platforms)
        if (emulatorsItemAtFocusIndex(state.focusedIndex, info) != EmulatorsItem.CloseOnSessionEnd) {
            return InputResult.UNHANDLED
        }
        return toggleLeftRight(direction, state.emulators.closeEmulatorOnSessionEnd) {
            viewModel.setCloseEmulatorOnSessionEnd(it)
        }
    }

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        if (state.focusedIndex != 0) return InputResult.UNHANDLED

        val wrapMode = state.controls.menuWrapMode
        val shouldWrap = wrapMode == MenuWrapMode.AUTO ||
            (wrapMode == MenuWrapMode.HARD_STOP && !currentIsRepeat)
        if (!shouldWrap) return InputResult.UNHANDLED

        val info = createEmulatorsLayoutInfo(state.emulators.platforms)
        val lastActive = info.layout.focusableItems(Unit).indexOfLast {
            it is EmulatorsItem.PlatformItem && it.config.platform.syncEnabled
        }
        if (lastActive >= 0) {
            viewModel.setFocusIndex(lastActive)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        val info = createEmulatorsLayoutInfo(state.emulators.platforms)
        val focused = emulatorsItemAtFocusIndex(state.focusedIndex, info)
        if (focused is EmulatorsItem.PlatformItem && focused.config.platform.syncEnabled) {
            val emulatorId = focused.config.effectiveEmulatorId ?: return InputResult.UNHANDLED
            if (emulatorId in state.emulators.emulatorUpdateVersions) {
                viewModel.triggerEmulatorUpdate(emulatorId)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        val info = createEmulatorsLayoutInfo(state.emulators.platforms)
        val focused = emulatorsItemAtFocusIndex(state.focusedIndex, info)
        if (focused is EmulatorsItem.PlatformItem && !focused.config.platform.syncEnabled) {
            viewModel.enablePlatformAndReload(focused.config.platform.id)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
