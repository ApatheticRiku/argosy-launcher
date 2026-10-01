package com.nendo.argosy.ui.screens.settings.delegates

import android.app.Application
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.UiShortcut
import com.nendo.argosy.ui.input.UiShortcutKeys
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.shortcutKey
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ControlsSettingsDelegate @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val hapticManager: HapticFeedbackManager,
    private val permissionHelper: PermissionHelper
) {
    private val _state = MutableStateFlow(ControlsState())
    val state: StateFlow<ControlsState> = _state.asStateFlow()

    fun updateState(newState: ControlsState) {
        _state.value = newState
    }

    fun setHapticEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setHapticEnabled(enabled)
            _state.update { it.copy(hapticEnabled = enabled) }
        }
    }

    fun getVibrationStrength(): Float = hapticManager.getSystemVibrationStrength()

    fun setVibrationStrength(strength: Float) {
        hapticManager.setSystemVibrationStrength(strength)
        _state.update { it.copy(vibrationStrength = strength) }
        hapticManager.vibrate(HapticPattern.STRENGTH_PREVIEW)
    }

    fun adjustVibrationStrength(delta: Float) {
        val current = _state.value.vibrationStrength
        val newStrength = (current + delta).coerceIn(0f, 1f)
        if (newStrength != current) {
            setVibrationStrength(newStrength)
        }
    }

    val supportsSystemVibration: Boolean
        get() = hapticManager.supportsSystemVibration

    fun setSwapAB(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapAB(enabled)
            _state.update { it.copy(swapAB = enabled) }
        }
    }

    fun setSwapXY(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapXY(enabled)
            _state.update { it.copy(swapXY = enabled) }
        }
    }

    fun setControllerLayout(scope: CoroutineScope, layout: String) {
        scope.launch {
            preferencesRepository.setControllerLayout(layout)
            _state.update { it.copy(controllerLayout = layout) }
        }
    }

    fun cycleControllerLayout(scope: CoroutineScope, direction: Int = 1) {
        val index = LAYOUT_CYCLE.indexOf(_state.value.controllerLayout).coerceAtLeast(0)
        setControllerLayout(scope, LAYOUT_CYCLE[(index + direction).mod(LAYOUT_CYCLE.size)])
    }

    fun refreshDetectedLayout() {
        val result = ControllerDetector.detectFromActiveGamepad()
        val layoutName = when (result.layout) {
            DetectedLayout.XBOX -> "Xbox"
            DetectedLayout.NINTENDO -> "Nintendo"
            null -> null
        }
        _state.update {
            it.copy(
                detectedLayout = layoutName,
                detectedDeviceName = result.deviceName
            )
        }
    }

    fun detectControllerLayout(): String? {
        val result = ControllerDetector.detectFromActiveGamepad()
        return when (result.layout) {
            DetectedLayout.XBOX -> "xbox"
            DetectedLayout.NINTENDO -> "nintendo"
            null -> null
        }
    }

    fun setSwapStartSelect(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapStartSelect(enabled)
            _state.update { it.copy(swapStartSelect = enabled) }
        }
    }

    fun setSelectLCombo(scope: CoroutineScope, value: String) {
        scope.launch {
            preferencesRepository.setSelectLCombo(value)
            _state.update { it.copy(selectLCombo = value) }
        }
    }

    fun setSelectRCombo(scope: CoroutineScope, value: String) {
        scope.launch {
            preferencesRepository.setSelectRCombo(value)
            _state.update { it.copy(selectRCombo = value) }
        }
    }

    fun cycleSelectLCombo(scope: CoroutineScope, direction: Int = 1) =
        setSelectLCombo(scope, cycleComboValue(_state.value.selectLCombo, direction))

    fun cycleSelectRCombo(scope: CoroutineScope, direction: Int = 1) =
        setSelectRCombo(scope, cycleComboValue(_state.value.selectRCombo, direction))

    companion object {
        val LAYOUT_CYCLE = listOf("auto", "xbox", "nintendo")
        val COMBO_CYCLE = listOf("quick_menu", "quick_settings", "none")

        fun cycleComboValue(current: String, direction: Int = 1): String {
            val index = COMBO_CYCLE.indexOf(current).coerceAtLeast(0)
            return COMBO_CYCLE[(index + direction).mod(COMBO_CYCLE.size)]
        }

        @StringRes
        fun comboDisplayNameRes(value: String): Int = when (value) {
            "quick_menu" -> R.string.settings_navigation_combo_quick_menu
            "quick_settings" -> R.string.settings_navigation_combo_quick_settings
            else -> R.string.settings_navigation_combo_none
        }

        @StringRes
        fun layoutDisplayNameRes(value: String): Int = when (value) {
            "nintendo" -> R.string.settings_navigation_controller_layout_nintendo
            "xbox" -> R.string.settings_navigation_controller_layout_xbox
            else -> R.string.settings_navigation_controller_layout_auto
        }
    }

    fun setMenuWrapMode(scope: CoroutineScope, mode: MenuWrapMode) {
        scope.launch {
            preferencesRepository.setMenuWrapMode(mode)
            _state.update { it.copy(menuWrapMode = mode) }
        }
    }

    fun cycleMenuWrapMode(scope: CoroutineScope, direction: Int = 1) {
        val current = _state.value.menuWrapMode
        setMenuWrapMode(scope, MenuWrapMode.entries[(current.ordinal + direction).mod(MenuWrapMode.entries.size)])
    }

    fun setSelectSwapMode(scope: CoroutineScope, mode: SelectSwapMode) {
        scope.launch {
            preferencesRepository.setSelectSwapMode(mode)
            _state.update { it.copy(selectSwapMode = mode) }
        }
    }

    fun cycleSelectSwapMode(scope: CoroutineScope, direction: Int = 1) {
        val current = _state.value.selectSwapMode
        setSelectSwapMode(scope, SelectSwapMode.entries[(current.ordinal + direction).mod(SelectSwapMode.entries.size)])
    }

    fun showNavRingModal() {
        _state.update {
            it.copy(
                showNavRingModal = true,
                navRingFocusIndex = 0,
                navRingHeld = null,
                navRingBackup = null
            )
        }
    }

    fun dismissNavRingModal() {
        _state.update { state ->
            state.copy(
                navRingRoutes = state.navRingBackup ?: state.navRingRoutes,
                showNavRingModal = false,
                navRingFocusIndex = 0,
                navRingHeld = null,
                navRingBackup = null
            )
        }
    }

    fun backNavRing() {
        if (_state.value.navRingHeld != null) cancelNavRingHold() else dismissNavRingModal()
    }

    fun focusNavRing(index: Int) {
        _state.update { state ->
            if (state.navRingHeld != null || index !in NavRing.rows(state.navRingRoutes).indices) {
                state
            } else {
                state.copy(navRingFocusIndex = index)
            }
        }
    }

    fun moveNavRingFocus(delta: Int) {
        _state.update { state ->
            val held = state.navRingHeld
            if (held != null) {
                reorderNavRing(state, held, state.navRingRoutes.indexOf(held) + delta)
            } else {
                val rowCount = NavRing.rows(state.navRingRoutes).size
                state.copy(navRingFocusIndex = (state.navRingFocusIndex + delta).mod(rowCount))
            }
        }
    }

    fun confirmNavRing(scope: CoroutineScope) {
        val state = _state.value
        if (state.navRingHeld != null) {
            dropNavRing(scope)
            return
        }
        NavRing.rows(state.navRingRoutes).getOrNull(state.navRingFocusIndex)?.let { toggleNavRing(scope, it) }
    }

    fun toggleNavRing(scope: CoroutineScope, token: String) {
        var committed: List<String>? = null
        _state.update { state ->
            if (state.navRingHeld != null) return@update state
            val updated = NavRing.toggle(state.navRingRoutes, token)
            if (updated == state.navRingRoutes) return@update state
            committed = updated
            state.copy(
                navRingRoutes = updated,
                navRingFocusIndex = NavRing.rows(updated).indexOf(token).coerceAtLeast(0)
            )
        }
        committed?.let { order -> scope.launch { preferencesRepository.setNavRingRoutes(order) } }
    }

    fun toggleNavRingLift(scope: CoroutineScope) {
        if (_state.value.navRingHeld != null) dropNavRing(scope) else liftNavRing()
    }

    fun liftNavRing() {
        _state.update { state ->
            if (state.navRingHeld != null) return@update state
            val token = NavRing.rows(state.navRingRoutes).getOrNull(state.navRingFocusIndex)
                ?: return@update state
            if (token !in state.navRingRoutes) return@update state
            state.copy(navRingHeld = token, navRingBackup = state.navRingRoutes)
        }
    }

    fun liftNavRingAt(token: String) {
        _state.update { state ->
            val index = state.navRingRoutes.indexOf(token)
            if (index == -1) return@update state
            state.copy(
                navRingHeld = token,
                navRingBackup = state.navRingBackup ?: state.navRingRoutes,
                navRingFocusIndex = index
            )
        }
    }

    fun moveNavRingTo(token: String, targetIndex: Int) {
        _state.update { state ->
            if (state.navRingHeld != token) state else reorderNavRing(state, token, targetIndex)
        }
    }

    fun dropNavRing(scope: CoroutineScope) {
        var committed: List<String>? = null
        _state.update { state ->
            if (state.navRingHeld == null) {
                committed = null
                state
            } else {
                committed = state.navRingRoutes.takeIf { it != state.navRingBackup }
                state.copy(navRingHeld = null, navRingBackup = null)
            }
        }
        committed?.let { order -> scope.launch { preferencesRepository.setNavRingRoutes(order) } }
    }

    fun cancelNavRingHold() {
        _state.update { state ->
            val backup = state.navRingBackup ?: state.navRingRoutes
            val held = state.navRingHeld
            state.copy(
                navRingRoutes = backup,
                navRingHeld = null,
                navRingBackup = null,
                navRingFocusIndex = held?.let { backup.indexOf(it) }?.takeIf { it >= 0 }
                    ?: state.navRingFocusIndex
            )
        }
    }

    private fun reorderNavRing(state: ControlsState, token: String, targetIndex: Int): ControlsState {
        val updated = NavRing.move(state.navRingRoutes, token, targetIndex)
        if (updated == state.navRingRoutes) return state
        return state.copy(navRingRoutes = updated, navRingFocusIndex = updated.indexOf(token))
    }

    fun startShortcutCapture(shortcut: UiShortcut) {
        _state.update { it.copy(shortcutCaptureTarget = shortcut) }
    }

    fun cancelShortcutCapture() {
        _state.update { it.copy(shortcutCaptureTarget = null) }
    }

    fun assignShortcutKey(scope: CoroutineScope, shortcut: UiShortcut, keyCode: Int) {
        if (!UiShortcutKeys.isBindable(keyCode)) return
        val current = _state.value
        val otherShortcut = when (shortcut) {
            UiShortcut.OPEN_NAVIGATION -> UiShortcut.OPEN_QUICK_PANEL
            UiShortcut.OPEN_QUICK_PANEL -> UiShortcut.OPEN_NAVIGATION
        }
        val otherHoldsKey = current.shortcutKey(otherShortcut) == keyCode
        _state.update { it.copy(shortcutCaptureTarget = null) }
        scope.launch {
            if (otherHoldsKey) writeShortcutKey(otherShortcut, ControlsPreferences.UNASSIGNED_KEY)
            writeShortcutKey(shortcut, keyCode)
        }
    }

    fun clearShortcutKey(scope: CoroutineScope, shortcut: UiShortcut) {
        scope.launch { writeShortcutKey(shortcut, ControlsPreferences.UNASSIGNED_KEY) }
    }

    private suspend fun writeShortcutKey(shortcut: UiShortcut, keyCode: Int) {
        when (shortcut) {
            UiShortcut.OPEN_NAVIGATION -> {
                preferencesRepository.setOpenNavigationKey(keyCode)
                _state.update { it.copy(openNavigationKey = keyCode) }
            }
            UiShortcut.OPEN_QUICK_PANEL -> {
                preferencesRepository.setOpenQuickPanelKey(keyCode)
                _state.update { it.copy(openQuickPanelKey = keyCode) }
            }
        }
    }

    fun refreshUsageStatsPermission() {
        val hasPermission = permissionHelper.hasUsageStatsPermission(application)
        _state.update { it.copy(hasUsageStatsPermission = hasPermission) }
    }

    fun openUsageStatsSettings() {
        permissionHelper.openUsageStatsSettings(application)
    }
}
