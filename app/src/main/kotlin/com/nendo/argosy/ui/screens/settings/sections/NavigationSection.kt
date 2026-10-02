package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.libretro.HotkeyManager
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.UiShortcut
import com.nendo.argosy.ui.input.UiShortcutKeys
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.NavRingPopup
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.ShortcutCaptureModal
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import kotlin.math.roundToInt

internal sealed class NavigationItem(
    val key: String,
    val section: String,
    val visibleWhen: (ControlsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (ControlsState) -> Boolean = { true }
    ) : NavigationItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (ControlsState) -> Boolean = { true })
        : NavigationItem(key, section, visibleWhen)

    data object ControllerLayout : NavigationItem("layout", "controller")
    data object SwapAB : NavigationItem("swapAB", "controller")
    data object SwapXY : NavigationItem("swapXY", "controller")
    data object SwapStartSelect : NavigationItem("swapStartSelect", "controller")

    data object HapticFeedback : NavigationItem("haptic", "feedback")
    data object VibrationStrength : NavigationItem(
        key = "vibration",
        section = "feedback",
        visibleWhen = { it.hapticEnabled }
    )

    data object MenuWrap : NavigationItem("menuWrap", "menus")
    data object SelectLCombo : NavigationItem("selectLCombo", "menus")
    data object SelectRCombo : NavigationItem("selectRCombo", "menus")
    data object SelectSwap : NavigationItem(
        key = "selectSwap",
        section = "menus",
        visibleWhen = { it.hasSecondaryDisplay }
    )

    data object QuickNavigation : NavigationItem("quickNavigation", "navbar")
    data object NavBarPages : NavigationItem(
        key = "navBarPages",
        section = "navbar",
        visibleWhen = { it.quickNavigation }
    )

    data object OpenNavigationShortcut : NavigationItem("openNavigationKey", "shortcuts")
    data object OpenQuickPanelShortcut : NavigationItem("openQuickPanelKey", "shortcuts")

    val shortcut: UiShortcut? get() = when (this) {
        OpenNavigationShortcut -> UiShortcut.OPEN_NAVIGATION
        OpenQuickPanelShortcut -> UiShortcut.OPEN_QUICK_PANEL
        else -> null
    }

    companion object {
        private val ControllerHeader =
            Header("controllerHeader", "controller", R.string.settings_navigation_section_controller)
        private val FeedbackSpacer = SectionSpacer("feedbackSpacer", "feedback")
        private val FeedbackHeader =
            Header("feedbackHeader", "feedback", R.string.settings_navigation_section_feedback)
        private val MenusSpacer = SectionSpacer("menusSpacer", "menus")
        private val MenusHeader = Header("menusHeader", "menus", R.string.settings_navigation_section_menus)
        private val NavBarSpacer = SectionSpacer("navBarSpacer", "navbar")
        private val NavBarHeader =
            Header("navBarHeader", "navbar", R.string.settings_navigation_section_nav_bar)
        private val ShortcutsSpacer = SectionSpacer("shortcutsSpacer", "shortcuts")
        private val ShortcutsHeader =
            Header("shortcutsHeader", "shortcuts", R.string.settings_navigation_section_shortcuts)
        val ALL: List<NavigationItem>
            get() = listOf(
                ControllerHeader,
                ControllerLayout, SwapAB, SwapXY, SwapStartSelect,
                FeedbackSpacer, FeedbackHeader,
                HapticFeedback, VibrationStrength,
                MenusSpacer, MenusHeader,
                MenuWrap, SelectLCombo, SelectRCombo, SelectSwap,
                NavBarSpacer, NavBarHeader,
                QuickNavigation, NavBarPages,
                ShortcutsSpacer, ShortcutsHeader,
                OpenNavigationShortcut, OpenQuickPanelShortcut
            )
    }
}

private val navigationLayout = SettingsLayout<NavigationItem, ControlsState>(
    allItems = NavigationItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "controller" -> R.string.settings_navigation_section_controller
            "feedback" -> R.string.settings_navigation_section_feedback
            "menus" -> R.string.settings_navigation_section_menus
            "navbar" -> R.string.settings_navigation_section_nav_bar
            "shortcuts" -> R.string.settings_navigation_section_shortcuts
            else -> null
        }
    }
)

internal fun navigationMaxFocusIndex(controls: ControlsState): Int = navigationLayout.maxFocusIndex(controls)

internal fun navigationItemAtFocusIndex(index: Int, controls: ControlsState): NavigationItem? =
    navigationLayout.itemAtFocusIndex(index, controls)

internal fun navigationSections(controls: ControlsState) = navigationLayout.buildSections(controls)

private fun menuWrapLabelRes(mode: MenuWrapMode): Int = when (mode) {
    MenuWrapMode.OFF -> R.string.settings_navigation_menu_wrap_off
    MenuWrapMode.HARD_STOP -> R.string.settings_navigation_menu_wrap_hard_stop
    MenuWrapMode.AUTO -> R.string.settings_navigation_menu_wrap_auto
}

@Composable
fun NavigationSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val controls = uiState.controls
    val context = LocalContext.current

    val visibleItems = remember(controls.hapticEnabled, controls.hasSecondaryDisplay, controls.quickNavigation) {
        navigationLayout.visibleItems(controls)
    }
    val sections = remember(controls.hapticEnabled, controls.hasSecondaryDisplay, controls.quickNavigation, context) {
        navigationLayout.buildSections(controls, context)
    }

    fun isFocused(item: NavigationItem): Boolean =
        uiState.focusedIndex == navigationLayout.focusIndexOf(item, controls)

    fun pickerToken(item: NavigationItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { navigationLayout.focusToListIndex(it, controls) },
        itemKey = { it.key },
        isNavItem = { it is NavigationItem.SectionSpacer },
        isHeader = { it is NavigationItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is NavigationItem.Header -> NavigationSectionHeader(stringResource(item.titleRes))
            is NavigationItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            NavigationItem.ControllerLayout -> {
                val layoutDisplay = stringResource(
                    ControlsSettingsDelegate.layoutDisplayNameRes(controls.controllerLayout)
                )
                val detected = controls.detectedLayout
                val device = controls.detectedDeviceName
                val subtitle = when {
                    detected != null && device != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected_device, detected, device)
                    detected != null ->
                        stringResource(R.string.settings_navigation_controller_layout_detected, detected)
                    else -> stringResource(R.string.settings_navigation_controller_layout_undetected)
                }
                CyclePreference(
                    title = stringResource(R.string.settings_navigation_controller_layout_title),
                    value = layoutDisplay,
                    subtitle = subtitle,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleControllerLayout() },
                    onPrev = { viewModel.cycleControllerLayout(-1) },
                    options = remember(context) {
                        ControlsSettingsDelegate.LAYOUT_CYCLE.map {
                            context.getString(ControlsSettingsDelegate.layoutDisplayNameRes(it))
                        }
                    },
                    onSelect = { viewModel.setControllerLayout(ControlsSettingsDelegate.LAYOUT_CYCLE[it]) },
                    pickerRequestToken = pickerToken(item)
                )
            }

            NavigationItem.SwapAB -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_ab_title),
                subtitle = stringResource(R.string.settings_navigation_swap_ab_subtitle),
                isEnabled = controls.swapAB,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapAB(it) }
            )

            NavigationItem.SwapXY -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_xy_title),
                subtitle = stringResource(R.string.settings_navigation_swap_xy_subtitle),
                isEnabled = controls.swapXY,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapXY(it) }
            )

            NavigationItem.SwapStartSelect -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_swap_start_select_title),
                subtitle = stringResource(R.string.settings_navigation_swap_start_select_subtitle),
                isEnabled = controls.swapStartSelect,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSwapStartSelect(it) }
            )

            NavigationItem.QuickNavigation -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_quick_navigation_title),
                subtitle = stringResource(R.string.settings_navigation_quick_navigation_subtitle),
                isEnabled = controls.quickNavigation,
                isFocused = isFocused(item),
                onToggle = { viewModel.setQuickNavigation(it) }
            )

            NavigationItem.HapticFeedback -> SwitchPreference(
                title = stringResource(R.string.settings_navigation_haptic_title),
                isEnabled = controls.hapticEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setHapticEnabled(it) }
            )

            NavigationItem.VibrationStrength -> SliderPreference(
                title = stringResource(R.string.settings_navigation_vibration_title),
                value = (controls.vibrationStrength * ControlsPreferences.HAPTIC_STRENGTH_STEPS).roundToInt() + 1,
                minValue = 1,
                maxValue = ControlsPreferences.HAPTIC_STRENGTH_STEPS + 1,
                isFocused = isFocused(item),
                onAdjust = {
                    viewModel.adjustVibrationStrength(
                        if (it < 0) -ControlsPreferences.HAPTIC_STRENGTH_STEP else ControlsPreferences.HAPTIC_STRENGTH_STEP
                    )
                }
            )

            NavigationItem.MenuWrap -> CyclePreference(
                title = stringResource(R.string.settings_navigation_menu_wrap_title),
                value = stringResource(menuWrapLabelRes(controls.menuWrapMode)),
                subtitle = stringResource(R.string.settings_navigation_menu_wrap_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleMenuWrapMode() },
                onPrev = { viewModel.cycleMenuWrapMode(-1) },
                options = remember(context) {
                    MenuWrapMode.entries.map { context.getString(menuWrapLabelRes(it)) }
                },
                onSelect = { viewModel.setMenuWrapMode(MenuWrapMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectLCombo -> CyclePreference(
                title = stringResource(R.string.settings_navigation_select_l_combo_title),
                value = stringResource(
                    ControlsSettingsDelegate.comboDisplayNameRes(controls.selectLCombo)
                ),
                subtitle = stringResource(R.string.settings_navigation_select_l_combo_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectLCombo() },
                onPrev = { viewModel.cycleSelectLCombo(-1) },
                options = remember(context) {
                    ControlsSettingsDelegate.COMBO_CYCLE.map {
                        context.getString(ControlsSettingsDelegate.comboDisplayNameRes(it))
                    }
                },
                onSelect = { viewModel.setSelectLCombo(ControlsSettingsDelegate.COMBO_CYCLE[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectRCombo -> CyclePreference(
                title = stringResource(R.string.settings_navigation_select_r_combo_title),
                value = stringResource(
                    ControlsSettingsDelegate.comboDisplayNameRes(controls.selectRCombo)
                ),
                subtitle = stringResource(R.string.settings_navigation_select_r_combo_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectRCombo() },
                onPrev = { viewModel.cycleSelectRCombo(-1) },
                options = remember(context) {
                    ControlsSettingsDelegate.COMBO_CYCLE.map {
                        context.getString(ControlsSettingsDelegate.comboDisplayNameRes(it))
                    }
                },
                onSelect = { viewModel.setSelectRCombo(ControlsSettingsDelegate.COMBO_CYCLE[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.SelectSwap -> CyclePreference(
                title = stringResource(R.string.settings_navigation_select_swap_title),
                value = stringResource(controls.selectSwapMode.labelRes),
                subtitle = stringResource(R.string.settings_navigation_select_swap_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSelectSwapMode() },
                onPrev = { viewModel.cycleSelectSwapMode(-1) },
                options = remember(context) {
                    SelectSwapMode.entries.map { context.getString(it.labelRes) }
                },
                onSelect = { viewModel.setSelectSwapMode(SelectSwapMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            NavigationItem.NavBarPages -> ActionPreference(
                title = stringResource(R.string.settings_navigation_nav_bar_pages_title),
                subtitle = stringResource(R.string.settings_navigation_nav_bar_pages_subtitle),
                isFocused = isFocused(item),
                trailingText = stringResource(
                    R.string.settings_navigation_nav_bar_pages_count,
                    controls.navRingRoutes.size,
                    NavRing.PAGES.size
                ),
                onClick = { viewModel.showNavRingModal() }
            )

            NavigationItem.OpenNavigationShortcut -> ShortcutKeyPreference(
                title = stringResource(R.string.settings_navigation_open_navigation_title),
                subtitle = stringResource(R.string.settings_navigation_open_navigation_subtitle),
                keyCode = controls.openNavigationKey,
                isFocused = isFocused(item),
                onClick = { viewModel.startShortcutCapture(UiShortcut.OPEN_NAVIGATION) }
            )

            NavigationItem.OpenQuickPanelShortcut -> ShortcutKeyPreference(
                title = stringResource(R.string.settings_navigation_open_quick_panel_title),
                subtitle = stringResource(R.string.settings_navigation_open_quick_panel_subtitle),
                keyCode = controls.openQuickPanelKey,
                isFocused = isFocused(item),
                onClick = { viewModel.startShortcutCapture(UiShortcut.OPEN_QUICK_PANEL) }
            )
        }
    }

    if (controls.showNavRingModal) {
        NavRingPopup(
            enabled = controls.navRingRoutes,
            focusIndex = controls.navRingFocusIndex,
            heldToken = controls.navRingHeld,
            onFocus = { viewModel.focusNavRing(it) },
            onToggle = { viewModel.toggleNavRing(it) },
            onLift = { viewModel.liftNavRing() },
            onLiftAt = { viewModel.liftNavRingAt(it) },
            onMoveTo = { token, index -> viewModel.moveNavRingTo(token, index) },
            onDrop = { viewModel.dropNavRing() },
            onBack = { viewModel.backNavRing() }
        )
    }

    controls.shortcutCaptureTarget?.let { target ->
        ShortcutCaptureModal(
            actionTitle = stringResource(shortcutTitleRes(target)),
            onAssign = { keyCode -> viewModel.assignShortcutKey(target, keyCode) },
            onDismiss = { viewModel.cancelShortcutCapture() }
        )
    }
}

private fun shortcutTitleRes(shortcut: UiShortcut): Int = when (shortcut) {
    UiShortcut.OPEN_NAVIGATION -> R.string.settings_navigation_open_navigation_title
    UiShortcut.OPEN_QUICK_PANEL -> R.string.settings_navigation_open_quick_panel_title
}

@Composable
private fun ShortcutKeyPreference(
    title: String,
    subtitle: String,
    keyCode: Int,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    ActionPreference(
        title = title,
        subtitle = subtitle,
        isFocused = isFocused,
        trailingText = if (UiShortcutKeys.isBindable(keyCode)) {
            HotkeyManager.getKeyName(keyCode)
        } else {
            stringResource(R.string.settings_navigation_shortcut_unassigned)
        },
        onClick = onClick
    )
}

@Composable
private fun NavigationSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
