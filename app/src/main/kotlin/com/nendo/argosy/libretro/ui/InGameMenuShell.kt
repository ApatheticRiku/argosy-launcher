package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
internal fun InGameMenuPanel(
    menuItems: List<Pair<Int, InGameMenuAction>>,
    openSection: InGameMenuSection?,
    focusedIndex: Int,
    railFocused: Boolean,
    condensedWidth: Dp,
    listHandler: InputHandler,
    isEnabled: (InGameMenuAction) -> Boolean,
    onFocusChange: (Int) -> Unit,
    onRailFocusChange: (Boolean) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    onCloseSection: () -> Unit,
    list: @Composable () -> Unit,
    sectionContent: @Composable (InGameMenuSection) -> InputHandler
): InputHandler {
    val currentMenuItems = rememberUpdatedState(menuItems)
    val currentOpenSection = rememberUpdatedState(openSection)
    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentIsEnabled = rememberUpdatedState(isEnabled)
    val currentRailFocused = rememberUpdatedState(railFocused)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnRailFocusChange = rememberUpdatedState(onRailFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)
    val currentOnCloseSection = rememberUpdatedState(onCloseSection)

    LaunchedEffect(openSection, menuItems) {
        val action = openSection?.action ?: return@LaunchedEffect
        val index = menuItems.indexOfFirst { it.second == action }
        if (index >= 0) currentOnFocusChange.value(index)
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val broadWidthPct = if (LocalUiScale.current.aspectRatioClass.isWide) {
        DimensionTokens.Layout.inGameMenuBroadWidthPctWide
    } else {
        DimensionTokens.Layout.inGameMenuBroadWidthPct
    }
    val panelWidth by animateDpAsState(
        targetValue = if (openSection != null) screenWidth * (broadWidthPct / 100f) else condensedWidth,
        animationSpec = tween(Motion.durationSlide, easing = Motion.argosyEase),
        label = "inGameMenuPanelWidth"
    )
    val railWidth = DimensionTokens.Layout.inGameMenuRailWidth.dp

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) {
        Color.Black.copy(alpha = SCRIM_ALPHA_DARK)
    } else {
        Color.White.copy(alpha = SCRIM_ALPHA_LIGHT)
    }
    val panelShape = RoundedCornerShape(topEnd = Dimens.radiusXl, bottomEnd = Dimens.radiusXl)

    var sectionHandler: InputHandler? = null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = gripReserveBottomInset())
            .clickableNoFocus {
                if (currentOpenSection.value != null) {
                    currentOnCloseSection.value()
                } else {
                    currentOnAction.value(InGameMenuAction.Resume)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = PANEL_SURFACE_ALPHA))
                .clickableNoFocus {}
                .focusProperties { canFocus = false }
        ) {
            val railModifier = if (openSection != null) {
                Modifier.width(railWidth).fillMaxHeight()
            } else {
                Modifier.weight(1f).fillMaxHeight()
            }
            AnimatedContent(
                targetState = openSection != null,
                modifier = railModifier,
                transitionSpec = {
                    fadeIn(tween(Motion.durationContent)) togetherWith
                        fadeOut(tween(Motion.durationContent)) using
                        SizeTransform(clip = true) { _, _ -> tween(Motion.durationSlide, easing = Motion.argosyEase) }
                },
                label = "inGameMenuListRail"
            ) { collapsed ->
                if (collapsed) {
                    InGameMenuRail(
                        menuItems = menuItems,
                        openAction = openSection?.action,
                        focusedIndex = focusedIndex,
                        railFocused = railFocused,
                        isEnabled = isEnabled,
                        onSelect = { action ->
                            val index = menuItems.indexOfFirst { it.second == action }
                            if (action.broadSection != null && index >= 0) onFocusChange(index)
                            onAction(action)
                        }
                    )
                } else {
                    list()
                }
            }
            if (openSection != null) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val contentAlpha = remember(openSection) { Animatable(0f) }
                LaunchedEffect(openSection) {
                    contentAlpha.animateTo(1f, tween(Motion.durationContent))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = contentAlpha.value }
                ) {
                    sectionHandler = key(openSection) { sectionContent(openSection) }
                }
            }
        }
    }

    val currentSectionHandler = rememberUpdatedState(sectionHandler)

    val shellHandler = remember {
        object : InputHandler {
            private val onRail: Boolean get() = currentRailFocused.value

            private fun forward(block: (InputHandler) -> InputResult): InputResult =
                currentSectionHandler.value?.let(block) ?: InputResult.HANDLED

            private fun railOr(block: (InputHandler) -> InputResult): InputResult =
                if (onRail) InputResult.HANDLED else forward(block)

            private fun stepRail(delta: Int): InputResult {
                val items = currentMenuItems.value
                val reachable = items.indices.filter { currentIsEnabled.value(items[it].second) }
                if (reachable.isEmpty()) return InputResult.HANDLED
                val current = currentFocusedIndex.value
                val position = reachable.indexOf(current)
                val target = if (position < 0) {
                    reachable.first()
                } else {
                    reachable[(position + delta).mod(reachable.size)]
                }
                if (target == current) return InputResult.HANDLED
                currentOnFocusChange.value(target)
                val action = items[target].second
                if (action.broadSection != null) currentOnAction.value(action)
                return InputResult.HANDLED
            }

            private fun leaveRail(): InputResult {
                val openAction = currentOpenSection.value?.action
                val openIndex = currentMenuItems.value.indexOfFirst { it.second == openAction }
                if (openIndex >= 0) currentOnFocusChange.value(openIndex)
                currentOnRailFocusChange.value(false)
                return InputResult.HANDLED
            }

            private fun confirmRail(): InputResult {
                val action = currentMenuItems.value.getOrNull(currentFocusedIndex.value)?.second
                    ?: return leaveRail()
                if (action.broadSection != null) return leaveRail()
                currentOnAction.value(action)
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult = if (onRail) stepRail(-1) else forward { it.onUp() }

            override fun onDown(): InputResult = if (onRail) stepRail(1) else forward { it.onDown() }

            override fun onLeft(): InputResult {
                if (onRail) return InputResult.HANDLED
                if (!forward { it.onLeft() }.handled) currentOnRailFocusChange.value(true)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult = if (onRail) leaveRail() else forward { it.onRight() }

            override fun onConfirm(): InputResult = if (onRail) confirmRail() else forward { it.onConfirm() }

            override fun onBack(): InputResult {
                if (!onRail) return forward { it.onBack() }
                currentOnCloseSection.value()
                return InputResult.HANDLED
            }

            override fun onMenu(): InputResult = railOr { it.onMenu() }
            override fun onSecondaryAction(): InputResult = railOr { it.onSecondaryAction() }
            override fun onContextMenu(): InputResult = railOr { it.onContextMenu() }
            override fun onPrevSection(): InputResult = railOr { it.onPrevSection() }
            override fun onNextSection(): InputResult = railOr { it.onNextSection() }
            override fun onPrevTrigger(): InputResult = railOr { it.onPrevTrigger() }
            override fun onNextTrigger(): InputResult = railOr { it.onNextTrigger() }
            override fun onSelect(): InputResult = railOr { it.onSelect() }
            override fun onLeftStickClick(): InputResult = railOr { it.onLeftStickClick() }
            override fun onRightStickClick(): InputResult = railOr { it.onRightStickClick() }
            override fun onLongConfirm(): InputResult = railOr { it.onLongConfirm() }
            override fun onLongSelect(): InputResult = railOr { it.onLongSelect() }
        }
    }

    return if (openSection == null) listHandler else shellHandler
}

private const val SCRIM_ALPHA_DARK = 0.7f
private const val SCRIM_ALPHA_LIGHT = 0.5f
private const val PANEL_SURFACE_ALPHA = 0.95f
