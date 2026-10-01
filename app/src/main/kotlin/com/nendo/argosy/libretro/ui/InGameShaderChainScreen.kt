package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.libretro.shader.ShaderChainManager
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.ShaderStackSection
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.gripReserveBottomInset

@Composable
fun InGameShaderChainScreen(
    manager: ShaderChainManager,
    onDismiss: () -> Unit
): InputHandler {
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f)
                       else Color.White.copy(alpha = 0.5f)

    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private val stack get() = manager.shaderStack

            override fun onUp(): InputResult {
                when {
                    stack.isReordering -> Unit
                    stack.showShaderPicker -> manager.moveShaderPickerFocus(-1)
                    else -> manager.moveShaderParamFocus(-1)
                }
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                when {
                    stack.isReordering -> Unit
                    stack.showShaderPicker -> manager.moveShaderPickerFocus(1)
                    else -> manager.moveShaderParamFocus(1)
                }
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult = when {
                stack.isReordering -> moveHeld(-1)
                stack.showShaderPicker -> InputResult.HANDLED
                else -> {
                    manager.adjustShaderParam(-1)
                    InputResult.HANDLED
                }
            }

            override fun onRight(): InputResult = when {
                stack.isReordering -> moveHeld(1)
                stack.showShaderPicker -> InputResult.HANDLED
                else -> {
                    manager.adjustShaderParam(1)
                    InputResult.HANDLED
                }
            }

            private fun moveHeld(delta: Int): InputResult =
                if (manager.moveHeldShader(delta)) InputResult.HANDLED
                else InputResult.handled(SoundType.BOUNDARY)

            override fun onConfirm(): InputResult {
                when {
                    stack.isReordering -> manager.dropShader()
                    stack.showShaderPicker -> manager.confirmShaderPickerSelection()
                    else -> manager.resetShaderParam()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                when {
                    stack.isReordering -> manager.cancelShaderLift()
                    stack.showShaderPicker -> manager.dismissShaderPicker()
                    else -> currentOnDismiss.value()
                }
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult {
                if (!stack.showShaderPicker && !stack.isReordering) {
                    manager.cycleShaderTab(-1)
                }
                return InputResult.HANDLED
            }

            override fun onNextSection(): InputResult {
                if (!stack.showShaderPicker && !stack.isReordering) {
                    manager.cycleShaderTab(1)
                }
                return InputResult.HANDLED
            }

            override fun onContextMenu(): InputResult {
                if (!stack.showShaderPicker && !stack.isReordering) {
                    manager.removeShaderFromStack()
                }
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                when {
                    stack.showShaderPicker -> Unit
                    stack.isReordering -> manager.dropShader()
                    else -> manager.liftShader()
                }
                return InputResult.HANDLED
            }

            override fun onSelect(): InputResult {
                if (!stack.showShaderPicker && !stack.isReordering) {
                    manager.showShaderPicker()
                }
                return InputResult.HANDLED
            }

            override fun onPrevTrigger(): InputResult = InputResult.HANDLED

            override fun onNextTrigger(): InputResult = InputResult.HANDLED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = gripReserveBottomInset())
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingLg)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(Dimens.radiusLg),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                ) {
                    ShaderStackSection(manager = manager)
                }

                FooterBar(
                    hints = buildShaderChainFooterHints(
                        pickerOpen = manager.shaderStack.showShaderPicker,
                        reordering = manager.shaderStack.isReordering,
                        hasEntries = manager.shaderStack.entries.isNotEmpty()
                    ),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.A -> inputHandler.onConfirm()
                            InputButton.B -> inputHandler.onBack()
                            InputButton.X -> inputHandler.onContextMenu()
                            InputButton.Y -> inputHandler.onSecondaryAction()
                            InputButton.SELECT -> inputHandler.onSelect()
                            else -> {}
                        }
                    }
                )
            }
        }
    }

    return inputHandler
}

@Composable
private fun buildShaderChainFooterHints(
    pickerOpen: Boolean,
    reordering: Boolean,
    hasEntries: Boolean
): List<Pair<InputButton, String>> {
    val browseLabel = stringResource(R.string.ingame_shader_footer_picker_browse)
    val pickerSelectLabel = stringResource(R.string.ingame_shader_footer_picker_select)
    val pickerCancelLabel = stringResource(R.string.ingame_shader_footer_picker_cancel)
    val shaderLabel = stringResource(R.string.ingame_shader_footer_shader)
    val reorderLabel = stringResource(R.string.ingame_shader_footer_reorder)
    val moveLabel = stringResource(R.string.ingame_shader_footer_move)
    val cancelLabel = stringResource(R.string.ingame_shader_footer_cancel)
    val adjustLabel = stringResource(R.string.ingame_shader_footer_adjust)
    val addLabel = stringResource(R.string.ingame_shader_footer_add)
    val removeLabel = stringResource(R.string.ingame_shader_footer_remove)
    val resetLabel = stringResource(R.string.ingame_shader_footer_reset)
    val backLabel = stringResource(R.string.ingame_shader_footer_back)
    val liftedHints = liftedReorderHints(
        move = moveLabel,
        cancel = cancelLabel,
        moveButton = InputButton.DPAD_HORIZONTAL
    )
    if (reordering) return liftedHints
    return buildList {
        if (pickerOpen) {
            add(InputButton.DPAD_VERTICAL to browseLabel)
            add(InputButton.A to pickerSelectLabel)
            add(InputButton.B to pickerCancelLabel)
        } else {
            if (hasEntries) {
                add(InputButton.LB_RB to shaderLabel)
                add(InputButton.Y to reorderLabel)
                add(InputButton.X to removeLabel)
            }
            add(InputButton.DPAD_HORIZONTAL to adjustLabel)
            add(InputButton.SELECT to addLabel)
            add(InputButton.A to resetLabel)
            add(InputButton.B to backLabel)
        }
    }
}
