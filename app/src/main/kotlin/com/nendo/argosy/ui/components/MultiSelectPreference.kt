package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus

private const val MODAL_SCRIM_ALPHA = 0.55f
private const val MODAL_MAX_HEIGHT_SHARE = 0.85f

/**
 * A settings row whose value is any subset of [options]. A or a tap opens a picker where A toggles
 * the focused option through [onToggle] and B closes it. [pickerRequestToken] opens the picker when
 * it rises, the same contract as [CyclePreference].
 */
@Composable
fun MultiSelectPreference(
    title: String,
    value: String,
    isFocused: Boolean,
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    emptyText: String,
    pickerRequestToken: Int = 0
) {
    var pickerVisible by remember { mutableStateOf(false) }
    var consumedPickerToken by remember { mutableIntStateOf(pickerRequestToken) }
    LaunchedEffect(pickerRequestToken) {
        if (pickerRequestToken > consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
            pickerVisible = true
        } else if (pickerRequestToken < consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
        }
    }

    Row(
        modifier = preferenceModifier(isFocused, onClick = { pickerVisible = true }),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) preferenceContentColor(true) else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    MultiSelectPickerModal(
        title = title,
        options = options,
        selected = selected,
        emptyText = emptyText,
        onToggle = onToggle,
        onDismiss = { pickerVisible = false },
        visible = pickerVisible
    )
}

@Composable
private fun MultiSelectPickerModal(
    title: String,
    options: List<String>,
    selected: Set<Int>,
    emptyText: String,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (!visibleState.currentState && !visibleState.targetState) return

    var focusedIndex by remember { mutableIntStateOf(0) }
    val optionCount by rememberUpdatedState(options.size)
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(visible) {
        if (visible) focusedIndex = 0
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (optionCount > 0) focusedIndex = (focusedIndex - 1).mod(optionCount)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (optionCount > 0) focusedIndex = (focusedIndex + 1).mod(optionCount)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (optionCount == 0) return InputResult.handled(SoundType.BOUNDARY)
                currentOnToggle(focusedIndex)
                return InputResult.handled(SoundType.TOGGLE)
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

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

    ModalInputEffect(active = visible, handler = inputHandler)

    Popup(properties = PopupProperties(focusable = false)) {
        val theme = LocalArgosyTheme.current
        val duration = Motion.durationDrawer / 2
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(duration, easing = Motion.argosyEase)),
            exit = fadeOut(animationSpec = tween(duration, easing = Motion.argosyEase))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = MODAL_SCRIM_ALPHA))
                    .padding(bottom = gripReserveBottomInset())
                    .clickableNoFocus { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = Dimens.modalWidthLg)
                        .heightIn(max = maxHeight * MODAL_MAX_HEIGHT_SHARE)
                        .clickableNoFocus { }
                ) {
                    GlassPanel {
                        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary
                            )
                            Spacer(Modifier.height(Dimens.spacingMd))
                            if (options.isEmpty()) {
                                Text(
                                    text = emptyText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.textDim
                                )
                            } else {
                                val listState = rememberLazyListState()
                                FocusedScroll(listState = listState, focusedIndex = focusedIndex)
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                                ) {
                                    itemsIndexed(options, key = { index, _ -> index }) { index, option ->
                                        MultiSelectRow(
                                            label = option,
                                            focused = index == focusedIndex,
                                            checked = index in selected,
                                            onClick = {
                                                focusedIndex = index
                                                onToggle(index)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectRow(label: String, focused: Boolean, checked: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators.ListRow,
                selected = checked,
                shape = RoundedCornerShape(Dimens.radiusControl)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}
