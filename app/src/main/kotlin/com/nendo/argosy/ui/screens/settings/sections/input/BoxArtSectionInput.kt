package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.stepOption
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.BoxArtItem
import com.nendo.argosy.ui.screens.settings.sections.GRADIENT_PRESET_CHOICES
import com.nendo.argosy.ui.screens.settings.sections.boxArtItemAtFocusIndex

internal class BoxArtSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult = stepShape(-1)

    override fun onNextSection(): InputResult = stepShape(1)

    override fun onPrevTrigger(): InputResult {
        viewModel.cyclePrevPreviewGame()
        return InputResult.HANDLED
    }

    override fun onNextTrigger(): InputResult {
        viewModel.cycleNextPreviewGame()
        return InputResult.HANDLED
    }

    private fun stepShape(direction: Int): InputResult =
        stepOption(BoxArtShape.entries, viewModel.uiState.value.display.boxArtShape, direction, viewModel::setBoxArtShape)

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val display = state.display
        when (boxArtItemAtFocusIndex(state.focusedIndex, display)) {
            BoxArtItem.Shape -> return stepShape(direction)
            BoxArtItem.CornerRadius -> viewModel.cycleBoxArtCornerRadius(direction)
            BoxArtItem.BorderThickness -> return stepOption(
                BoxArtBorderThickness.entries,
                display.boxArtBorderThickness,
                direction,
                viewModel::setBoxArtBorderThickness
            )
            BoxArtItem.BorderStyle -> return stepOption(
                BoxArtBorderStyle.entries,
                display.boxArtBorderStyle,
                direction,
                viewModel::setBoxArtBorderStyle
            )
            BoxArtItem.GlassTint -> viewModel.cycleGlassBorderTint(direction)
            BoxArtItem.GradientPresetItem -> return stepOption(
                GRADIENT_PRESET_CHOICES,
                display.gradientPreset,
                direction,
                viewModel::setGradientPreset
            )
            BoxArtItem.GradientAdvanced ->
                return toggleLeftRight(direction, display.gradientAdvancedMode) { viewModel.toggleGradientAdvancedMode() }
            BoxArtItem.SampleGrid -> viewModel.cycleGradientSampleGrid(direction)
            BoxArtItem.SampleRadius -> viewModel.cycleGradientRadius(direction)
            BoxArtItem.MinSaturation -> viewModel.cycleGradientMinSaturation(direction)
            BoxArtItem.MinBrightness -> viewModel.cycleGradientMinValue(direction)
            BoxArtItem.HueDistance -> viewModel.cycleGradientHueDistance(direction)
            BoxArtItem.SaturationBoost -> viewModel.cycleGradientSaturationBump(direction)
            BoxArtItem.BrightnessClamp -> viewModel.cycleGradientValueClamp(direction)
            BoxArtItem.IndicatorStyle -> return stepOption(
                PlatformIndicatorStyle.entries,
                display.platformIndicatorStyle,
                direction,
                viewModel::setPlatformIndicatorStyle
            )
            BoxArtItem.IndicatorContent -> return stepOption(
                PlatformIndicatorContent.entries,
                display.platformIndicatorContent,
                direction,
                viewModel::setPlatformIndicatorContent
            )
            BoxArtItem.IconPos -> viewModel.cycleSystemIconPosition(direction)
            BoxArtItem.IconPad -> return stepOption(
                SystemIconPadding.entries,
                display.systemIconPadding,
                direction,
                viewModel::setSystemIconPadding
            )
            BoxArtItem.OuterEffect -> return stepOption(
                BoxArtOuterEffect.entries,
                display.boxArtOuterEffect,
                direction,
                viewModel::setBoxArtOuterEffect
            )
            BoxArtItem.OuterThickness -> return stepOption(
                BoxArtOuterEffectThickness.entries,
                display.boxArtOuterEffectThickness,
                direction,
                viewModel::setBoxArtOuterEffectThickness
            )
            BoxArtItem.GlowIntensity -> viewModel.cycleBoxArtGlowStrength(direction)
            BoxArtItem.GlowColor -> return stepOption(
                GlowColorMode.entries,
                display.glowColorMode,
                direction,
                viewModel::setGlowColorMode
            )
            BoxArtItem.InnerEffect -> viewModel.cycleBoxArtInnerEffect(direction)
            BoxArtItem.InnerThickness -> return stepOption(
                BoxArtInnerEffectThickness.entries,
                display.boxArtInnerEffectThickness,
                direction,
                viewModel::setBoxArtInnerEffectThickness
            )
            else -> {}
        }
        return InputResult.HANDLED
    }
}
