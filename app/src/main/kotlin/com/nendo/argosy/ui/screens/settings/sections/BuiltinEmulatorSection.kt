package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.HudCorner
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ARCHITECTURE_OPTIONS
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.hudCornerFromStored

internal enum class BuiltinEmulatorItem {
    ENABLE,
    ARCHITECTURE,
    VIDEO,
    CONTROLS,
    CORE_MANAGEMENT,
    CORE_OPTIONS,
    TWO_COLUMN,
    HUD_ENABLED,
    HUD_CORNER,
    HUD_BATTERY,
    HUD_CLOCK,
    HUD_PLAYTIME,
    HUD_FPS,
    HUD_LAST_SAVE;

    val focusIndex: Int get() = ordinal
}

@Composable
fun BuiltinEmulatorSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val emulators = uiState.emulators
    val builtinEnabled = emulators.builtinLibretroEnabled
    val listState = rememberLazyListState()

    val statusBarHeaderOffset =
        if (builtinEnabled && uiState.focusedIndex >= BuiltinEmulatorItem.HUD_ENABLED.focusIndex) 1 else 0

    FocusedScroll(listState = listState, focusedIndex = uiState.focusedIndex + statusBarHeaderOffset)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item(key = "builtin_toggle") {
            SwitchPreference(
                title = stringResource(R.string.settings_builtin_enable_title),
                subtitle = stringResource(R.string.settings_builtin_enable_subtitle),
                isEnabled = builtinEnabled,
                isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ENABLE.focusIndex,
                onToggle = { viewModel.setBuiltinLibretroEnabled(it) }
            )
        }
        if (builtinEnabled) {
            item(key = "builtin_architecture") {
                val architectureOptions = remember { ARCHITECTURE_OPTIONS }
                SegmentedPreference(
                    title = stringResource(R.string.settings_builtin_architecture_title),
                    options = architectureOptions,
                    selectedIndex = architectureOptions.indexOf(emulators.architectureDisplay),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.ARCHITECTURE.focusIndex,
                    onSelect = { viewModel.setBuiltinArchitecture(architectureOptions[it]) }
                )
            }
            item(key = "builtin_video") {
                ActionPreference(
                    title = stringResource(R.string.settings_builtin_video_title),
                    subtitle = stringResource(R.string.settings_builtin_video_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.VIDEO.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.VIDEO.focusIndex)
                        viewModel.navigateToBuiltinVideo()
                    }
                )
            }
            item(key = "builtin_controls") {
                ActionPreference(
                    title = stringResource(R.string.settings_builtin_controls_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CONTROLS.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CONTROLS.focusIndex)
                        viewModel.navigateToBuiltinControls()
                    }
                )
            }
            item(key = "builtin_cores") {
                val updatesAvailable = emulators.coreUpdatesAvailable
                ActionPreference(
                    title = stringResource(R.string.settings_builtin_cores_title),
                    subtitle = stringResource(
                        R.string.settings_builtin_cores_subtitle,
                        emulators.installedCoreCount,
                        emulators.totalCoreCount
                    ),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CORE_MANAGEMENT.focusIndex)
                        viewModel.navigateToCoreManagement()
                    },
                    badge = if (updatesAvailable > 0) {
                        pluralStringResource(
                            R.plurals.settings_builtin_cores_updates_badge,
                            updatesAvailable,
                            updatesAvailable
                        )
                    } else {
                        null
                    }
                )
            }
            item(key = "builtin_core_options") {
                ActionPreference(
                    title = stringResource(R.string.settings_builtin_core_options_title),
                    subtitle = stringResource(R.string.settings_builtin_core_options_subtitle),
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.CORE_OPTIONS.focusIndex,
                    onClick = {
                        viewModel.setFocusIndex(BuiltinEmulatorItem.CORE_OPTIONS.focusIndex)
                        viewModel.navigateToCoreOptions()
                    }
                )
            }
            item(key = "builtin_two_column_menu") {
                SwitchPreference(
                    title = stringResource(R.string.settings_builtin_two_column_title),
                    subtitle = if (emulators.ingameMenuTwoColumn) {
                        stringResource(R.string.settings_builtin_two_column_subtitle_on)
                    } else {
                        stringResource(R.string.settings_builtin_two_column_subtitle_off)
                    },
                    isEnabled = emulators.ingameMenuTwoColumn,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.TWO_COLUMN.focusIndex,
                    onToggle = { viewModel.setIngameMenuTwoColumn(it) }
                )
            }
            item(key = "builtin_hud_header") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                SectionHeader(stringResource(R.string.settings_builtin_section_hud))
            }
            item(key = "builtin_hud_enabled") {
                SwitchPreference(
                    title = stringResource(R.string.settings_builtin_hud_enabled_title),
                    subtitle = if (emulators.hudEnabled) {
                        stringResource(R.string.settings_builtin_hud_enabled_subtitle_on)
                    } else {
                        stringResource(R.string.settings_builtin_hud_enabled_subtitle_off)
                    },
                    isEnabled = emulators.hudEnabled,
                    isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_ENABLED.focusIndex,
                    onToggle = { viewModel.setHudEnabled(it) }
                )
            }
            if (emulators.hudEnabled) {
                item(key = "builtin_hud_corner") {
                    SegmentedPreference(
                        title = stringResource(R.string.settings_builtin_hud_corner_title),
                        options = HudCorner.entries.map { stringResource(it.labelRes) },
                        selectedIndex = hudCornerFromStored(emulators.hudCorner).ordinal,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CORNER.focusIndex,
                        onSelect = { index -> viewModel.setHudCorner(HudCorner.entries[index].name) }
                    )
                }
                item(key = "builtin_hud_battery") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_builtin_hud_battery_title),
                        isEnabled = emulators.hudShowBattery,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_BATTERY.focusIndex,
                        onToggle = { viewModel.setHudShowBattery(it) }
                    )
                }
                item(key = "builtin_hud_clock") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_builtin_hud_clock_title),
                        isEnabled = emulators.hudShowClock,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_CLOCK.focusIndex,
                        onToggle = { viewModel.setHudShowClock(it) }
                    )
                }
                item(key = "builtin_hud_playtime") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_builtin_hud_playtime_title),
                        subtitle = stringResource(R.string.settings_builtin_hud_playtime_subtitle),
                        isEnabled = emulators.hudShowPlaytime,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_PLAYTIME.focusIndex,
                        onToggle = { viewModel.setHudShowPlaytime(it) }
                    )
                }
                item(key = "builtin_hud_fps") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_builtin_hud_fps_title),
                        subtitle = stringResource(R.string.settings_builtin_hud_fps_subtitle),
                        isEnabled = emulators.hudShowFps,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_FPS.focusIndex,
                        onToggle = { viewModel.setHudShowFps(it) }
                    )
                }
                item(key = "builtin_hud_last_save") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_builtin_hud_last_save_title),
                        subtitle = stringResource(R.string.settings_builtin_hud_last_save_subtitle),
                        isEnabled = emulators.hudShowLastSave,
                        isFocused = uiState.focusedIndex == BuiltinEmulatorItem.HUD_LAST_SAVE.focusIndex,
                        onToggle = { viewModel.setHudShowLastSave(it) }
                    )
                }
            }
        }
    }
}
