package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import com.nendo.argosy.ui.primitives.ArgosyTrackSlider
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val LABEL_WEIGHT = 3f
private const val VALUE_WEIGHT = 2f

@Composable
internal fun quickFocusBackground(isFocused: Boolean): Color =
    if (isFocused) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f) else Color.Transparent

enum class FanMode(val value: Int, @StringRes val labelRes: Int) {
    QUIET(1, R.string.ui_quick_settings_fan_quiet),
    SMART(4, R.string.ui_quick_settings_fan_smart),
    SPORT(5, R.string.ui_quick_settings_fan_sport),
    CUSTOM(6, R.string.ui_quick_settings_fan_custom);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SMART
    }
}

enum class PerformanceMode(val value: Int, @StringRes val labelRes: Int) {
    STANDARD(0, R.string.ui_quick_settings_performance_standard),
    HIGH(1, R.string.ui_quick_settings_performance_high),
    MAX(2, R.string.ui_quick_settings_performance_max);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: STANDARD
    }
}

data class QuickSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,
    val vibrationSupported: Boolean = false,
    val fanMode: FanMode = FanMode.SMART,
    val fanSpeed: Int = 25000,
    val performanceMode: PerformanceMode = PerformanceMode.STANDARD,
    val deviceSettingsSupported: Boolean = false,
    val deviceSettingsEnabled: Boolean = false,
    val systemVolume: Float = 1f,
    val screenBrightness: Float = 0.5f,
    val isDualScreenActive: Boolean = false,
    val isRolesSwapped: Boolean = false,
    val isSocialLinked: Boolean = false,
    val quayPassEnabled: Boolean = false
)

enum class QuickSettingsPage(val icon: ImageVector, @StringRes val titleRes: Int) {
    FRIENDS(Icons.Default.Group, R.string.ui_quick_settings_page_friends),
    QUICK(Icons.Default.Tune, R.string.ui_quick_settings_title),
    SCREENS(Icons.Outlined.Monitor, R.string.ui_quick_settings_page_screens),
    PERFORMANCE(Icons.Default.Speed, R.string.ui_quick_settings_page_performance),
    MUSIC(Icons.Default.MusicNote, R.string.ui_quick_settings_page_music)
}

enum class QuickSettingsGroup(val page: QuickSettingsPage, @StringRes val titleRes: Int) {
    DISPLAY(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_display),
    AUDIO(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_audio),
    OTHER(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_other)
}

sealed class QuickSettingsItem(
    val key: String,
    val page: QuickSettingsPage,
    val group: QuickSettingsGroup? = null,
    val visibleWhen: (QuickSettingsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(val headerGroup: QuickSettingsGroup) :
        QuickSettingsItem("header_${headerGroup.name}", headerGroup.page, headerGroup)

    data object Theme : QuickSettingsItem("theme", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY)
    data object ScreenBrightness : QuickSettingsItem(
        "screenBrightness", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY
    )
    data object SystemVolume : QuickSettingsItem("systemVolume", QuickSettingsPage.QUICK, QuickSettingsGroup.AUDIO)
    data object UISounds : QuickSettingsItem("uiSounds", QuickSettingsPage.QUICK, QuickSettingsGroup.AUDIO)
    data object Haptic : QuickSettingsItem("haptic", QuickSettingsPage.QUICK, QuickSettingsGroup.OTHER)
    data object VibrationStrength : QuickSettingsItem(
        "vibrationStrength", QuickSettingsPage.QUICK, QuickSettingsGroup.OTHER,
        visibleWhen = { it.vibrationSupported && it.hapticEnabled }
    )

    data object SwapDisplays : QuickSettingsItem(
        "swapDisplays", QuickSettingsPage.SCREENS,
        visibleWhen = { it.isDualScreenActive }
    )

    data object Performance : QuickSettingsItem(
        "performance", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.deviceSettingsSupported }
    )
    data object Fan : QuickSettingsItem(
        "fan", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.deviceSettingsSupported }
    )
    data object FanSpeed : QuickSettingsItem(
        "fanSpeed", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.deviceSettingsSupported && it.deviceSettingsEnabled && it.fanMode == FanMode.CUSTOM }
    )

    data object QuayPass : QuickSettingsItem(
        "quaypass", QuickSettingsPage.FRIENDS,
        visibleWhen = { it.isSocialLinked }
    )

    data object MusicPlayer : QuickSettingsItem("musicPlayer", QuickSettingsPage.MUSIC)

    companion object {
        private val DisplayHeader = Header(QuickSettingsGroup.DISPLAY)
        private val AudioHeader = Header(QuickSettingsGroup.AUDIO)
        private val OtherHeader = Header(QuickSettingsGroup.OTHER)

        val ALL: List<QuickSettingsItem>
            get() = listOf(
                QuayPass,
                DisplayHeader, Theme, ScreenBrightness,
                AudioHeader, SystemVolume, UISounds,
                OtherHeader, Haptic, VibrationStrength,
                SwapDisplays,
                Performance, Fan, FanSpeed,
                MusicPlayer
            )
    }
}

private val quickSettingsLayouts: Map<QuickSettingsPage, SettingsLayout<QuickSettingsItem, QuickSettingsState>> =
    QuickSettingsPage.entries.associateWith { page ->
        SettingsLayout(
            allItems = QuickSettingsItem.ALL.filter { it.page == page },
            isFocusable = { it.isFocusable },
            visibleWhen = { item, state -> item.visibleWhen(state) },
            sectionOf = { it.group?.name ?: it.page.name }
        )
    }

private fun quickSettingsLayoutFor(page: QuickSettingsPage): SettingsLayout<QuickSettingsItem, QuickSettingsState> =
    quickSettingsLayouts.getValue(page)

fun quickSettingsVisiblePages(state: QuickSettingsState): List<QuickSettingsPage> =
    QuickSettingsPage.entries.filter { quickSettingsLayoutFor(it).focusableItems(state).isNotEmpty() }

fun quickSettingsEffectivePage(page: QuickSettingsPage, state: QuickSettingsState): QuickSettingsPage {
    val pages = quickSettingsVisiblePages(state)
    return when {
        page in pages -> page
        QuickSettingsPage.QUICK in pages -> QuickSettingsPage.QUICK
        else -> pages.firstOrNull() ?: QuickSettingsPage.QUICK
    }
}

fun quickSettingsMaxFocusIndex(page: QuickSettingsPage, state: QuickSettingsState): Int =
    quickSettingsLayoutFor(page).maxFocusIndex(state)

fun quickSettingsItemAtFocusIndex(
    page: QuickSettingsPage,
    index: Int,
    state: QuickSettingsState
): QuickSettingsItem? = quickSettingsLayoutFor(page).itemAtFocusIndex(index, state)

@Composable
fun QuickSettingsPanel(
    isVisible: Boolean,
    state: QuickSettingsState,
    page: QuickSettingsPage,
    focusedIndex: Int,
    onPageSelect: (QuickSettingsPage) -> Unit,
    onThemeCycle: () -> Unit,
    onSoundToggle: () -> Unit,
    onHapticToggle: () -> Unit,
    onVibrationStrengthChange: (Float) -> Unit,
    onFanModeCycle: () -> Unit,
    onFanSpeedChange: (Int) -> Unit,
    onPerformanceModeCycle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onQuayPassToggle: () -> Unit = {},
    onSwapDisplays: () -> Unit = {},
    musicPage: @Composable () -> Unit = {},
    onDismiss: () -> Unit,
    footerHints: List<Pair<InputButton, String>> = listOf(
        InputButton.B to stringResource(R.string.ui_quick_settings_footer_close)
    ),
    onHintClick: ((InputButton) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val permissionMissing = state.deviceSettingsSupported && !state.deviceSettingsEnabled
    val pages = remember(state) { quickSettingsVisiblePages(state) }
    val activePage = remember(page, state) { quickSettingsEffectivePage(page, state) }
    val layout = quickSettingsLayoutFor(activePage)
    val visibleItems = remember(activePage, state) { layout.visibleItems(state) }
    val sections = remember(activePage, state) { layout.buildSections(state) }

    fun isFocused(item: QuickSettingsItem): Boolean =
        focusedIndex == layout.focusIndexOf(item, state)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickableNoFocus(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Row(
                modifier = Modifier
                    .width(Dimens.modalWidth - Dimens.footerHeight)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = Dimens.spacingLg)
                ) {
                    Text(
                        text = stringResource(activePage.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg)
                    )

                    Spacer(modifier = Modifier.height(Dimens.spacingSm))

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    val listState = key(activePage) { rememberLazyListState() }

                    SectionFocusedScroll(
                        listState = listState,
                        focusedIndex = focusedIndex,
                        focusToListIndex = { layout.focusToListIndex(it, state) },
                        sections = sections
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(visibleItems, key = { it.key }) { item ->
                            when (item) {
                                is QuickSettingsItem.Header -> QuickSettingsGroupHeader(
                                    title = stringResource(item.headerGroup.titleRes)
                                )

                                QuickSettingsItem.Performance -> QuickSettingItemTwoLine(
                                    icon = Icons.Default.Speed,
                                    label = stringResource(R.string.ui_quick_settings_performance),
                                    value = stringResource(state.performanceMode.labelRes),
                                    isFocused = isFocused(item),
                                    isDisabled = permissionMissing,
                                    disabledReason = stringResource(
                                        R.string.ui_quick_settings_performance_disabled
                                    ),
                                    onClick = onPerformanceModeCycle
                                )

                                QuickSettingsItem.Fan -> QuickSettingItem(
                                    icon = Icons.Default.Toys,
                                    label = stringResource(R.string.ui_quick_settings_fan),
                                    value = stringResource(state.fanMode.labelRes),
                                    isFocused = isFocused(item),
                                    isDisabled = permissionMissing,
                                    disabledReason = stringResource(
                                        R.string.ui_quick_settings_fan_disabled
                                    ),
                                    onClick = onFanModeCycle
                                )

                                QuickSettingsItem.FanSpeed -> FanSpeedSlider(
                                    speed = state.fanSpeed,
                                    isFocused = isFocused(item),
                                    onSpeedChange = onFanSpeedChange
                                )

                                QuickSettingsItem.Theme -> QuickSettingItem(
                                    icon = when (state.themeMode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                    },
                                    label = stringResource(R.string.ui_quick_settings_theme),
                                    value = state.themeMode.displayName,
                                    isFocused = isFocused(item),
                                    onClick = onThemeCycle
                                )

                                QuickSettingsItem.SystemVolume -> SystemVolumeSlider(
                                    volume = state.systemVolume,
                                    isFocused = isFocused(item),
                                    onVolumeChange = onVolumeChange
                                )

                                QuickSettingsItem.ScreenBrightness -> ScreenBrightnessSlider(
                                    brightness = state.screenBrightness,
                                    isFocused = isFocused(item),
                                    onBrightnessChange = onBrightnessChange
                                )

                                QuickSettingsItem.Haptic -> QuickSettingToggle(
                                    icon = Icons.Default.Vibration,
                                    label = stringResource(R.string.ui_quick_settings_haptics),
                                    isEnabled = state.hapticEnabled,
                                    isFocused = isFocused(item),
                                    onClick = onHapticToggle
                                )

                                QuickSettingsItem.VibrationStrength -> VibrationStrengthSlider(
                                    strength = state.vibrationStrength,
                                    isFocused = isFocused(item),
                                    onStrengthChange = onVibrationStrengthChange
                                )

                                QuickSettingsItem.UISounds -> QuickSettingToggle(
                                    icon = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    label = stringResource(R.string.ui_quick_settings_ui_sounds),
                                    isEnabled = state.soundEnabled,
                                    isFocused = isFocused(item),
                                    onClick = onSoundToggle
                                )

                                QuickSettingsItem.MusicPlayer -> Box(
                                    modifier = Modifier.fillParentMaxSize()
                                ) {
                                    musicPage()
                                }

                                QuickSettingsItem.SwapDisplays -> QuickSettingToggle(
                                    icon = Icons.Default.SwapHoriz,
                                    label = stringResource(R.string.ui_quick_settings_swap_displays),
                                    isEnabled = state.isRolesSwapped,
                                    isFocused = isFocused(item),
                                    onClick = onSwapDisplays
                                )

                                QuickSettingsItem.QuayPass -> QuickSettingToggle(
                                    icon = if (state.quayPassEnabled) QuayPassIcons.On else QuayPassIcons.Off,
                                    label = stringResource(R.string.ui_quick_settings_quaypass),
                                    isEnabled = state.quayPassEnabled,
                                    isFocused = isFocused(item),
                                    onClick = onQuayPassToggle
                                )
                            }
                        }
                    }

                    FooterHints(hints = footerHints, onHintClick = onHintClick)
                    FooterSpacer()
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                QuickSettingsRail(
                    pages = pages,
                    activePage = activePage,
                    onPageSelect = onPageSelect
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Dimens.spacingLg,
            end = Dimens.spacingLg,
            top = Dimens.spacingSm,
            bottom = Dimens.spacingXs
        )
    )
}

@Composable
private fun QuickSettingsRail(
    pages: List<QuickSettingsPage>,
    activePage: QuickSettingsPage,
    onPageSelect: (QuickSettingsPage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = Dimens.spacingXs, vertical = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        pages.forEach { page ->
            key(page) {
                QuickSettingsRailIcon(
                    page = page,
                    isActive = page == activePage,
                    onClick = { onPageSelect(page) }
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsRailIcon(
    page: QuickSettingsPage,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .argosyFocusIndicators(
                focused = isActive,
                indicators = FocusIndicators.Pill,
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = stringResource(page.titleRes),
            tint = if (isActive) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
internal fun QuickSettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    isFocused: Boolean,
    isDisabled: Boolean = false,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isDisabled -> Color.Transparent
        else -> quickFocusBackground(isFocused)
    }

    val contentColor = when {
        isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val valueColor = when {
        isDisabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .then(if (isDisabled) Modifier else Modifier.clickableNoFocus(onClick = onClick))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(LABEL_WEIGHT)
        )
        val shownValue = if (isDisabled && disabledReason != null) disabledReason else value
        if (shownValue.isNotEmpty()) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = shownValue,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(VALUE_WEIGHT, fill = false)
            )
        }
    }
}

@Composable
private fun QuickSettingItemTwoLine(
    icon: ImageVector,
    label: String,
    value: String,
    isFocused: Boolean,
    isDisabled: Boolean = false,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isDisabled -> Color.Transparent
        else -> quickFocusBackground(isFocused)
    }

    val contentColor = when {
        isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val valueColor = when {
        isDisabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .then(if (isDisabled) Modifier else Modifier.clickableNoFocus(onClick = onClick))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Text(
            text = if (isDisabled && disabledReason != null) disabledReason else value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingXs),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
internal fun QuickSettingToggle(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = quickFocusBackground(isFocused)

    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        ArgosyToggle(
            checked = isEnabled,
            onToggle = { onClick() },
            focused = isFocused
        )
    }
}

@Composable
private fun FanSpeedSlider(
    speed: Int,
    isFocused: Boolean,
    onSpeedChange: (Int) -> Unit
) {
    val minSpeed = 25000f
    val maxSpeed = 35000f
    val percentage = ((speed - minSpeed) / (maxSpeed - minSpeed) * 100).toInt()

    val backgroundColor = quickFocusBackground(isFocused)

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.ui_quick_settings_fan_speed),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ArgosyTrackSlider(
            value = speed.toFloat(),
            onValueChange = { onSpeedChange(it.toInt()) },
            minValue = minSpeed,
            maxValue = maxSpeed,
            focused = isFocused
        )
    }
}

@Composable
private fun VibrationStrengthSlider(
    strength: Float,
    isFocused: Boolean,
    onStrengthChange: (Float) -> Unit
) {
    val percentage = (strength * 100).toInt()

    val backgroundColor = quickFocusBackground(isFocused)

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.ui_quick_settings_vibration_strength),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ArgosyTrackSlider(
            value = strength,
            onValueChange = onStrengthChange,
            focused = isFocused
        )
    }
}

@Composable
private fun SystemVolumeSlider(
    volume: Float,
    isFocused: Boolean,
    onVolumeChange: (Float) -> Unit,
    label: String = stringResource(R.string.ui_quick_settings_volume)
) {
    val percentage = (volume * 100).toInt()

    val backgroundColor = quickFocusBackground(isFocused)

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (volume > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ArgosyTrackSlider(
            value = volume,
            onValueChange = onVolumeChange,
            focused = isFocused
        )
    }
}

@Composable
private fun ScreenBrightnessSlider(
    brightness: Float,
    isFocused: Boolean,
    onBrightnessChange: (Float) -> Unit,
    label: String = stringResource(R.string.ui_quick_settings_brightness)
) {
    val percentage = (brightness * 100).toInt()

    val backgroundColor = quickFocusBackground(isFocused)

    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsBrightness,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ArgosyTrackSlider(
            value = brightness,
            onValueChange = onBrightnessChange,
            focused = isFocused
        )
    }
}
