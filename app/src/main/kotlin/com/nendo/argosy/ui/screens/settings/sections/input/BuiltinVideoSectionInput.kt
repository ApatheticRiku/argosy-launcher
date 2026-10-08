package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.GlobalLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.isAtSegmentEnd
import com.nendo.argosy.ui.screens.settings.libretro.isSegmented
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex

internal enum class BuiltinFolder { SAVE, STATE }

internal data class BuiltinFolderRow(
    val folder: BuiltinFolder,
    val platformId: Long?,
    val resettable: Boolean
)

internal fun focusedBuiltinFolderRow(state: SettingsUiState): BuiltinFolderRow? {
    val videoState = state.builtinVideo
    if (videoState.savePath.isEmpty()) return null
    val isGlobal = videoState.isGlobalContext
    val platformContext = videoState.currentPlatformContext
    val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
    val maxSettingsIndex = libretroSettingsMaxFocusIndex(
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion
    )
    val resetAllExtra = if (!isGlobal && platformSettings?.hasAnyOverrides() == true) 1 else 0
    val platformId = if (isGlobal) null else platformContext?.platformId
    return when (state.focusedIndex) {
        maxSettingsIndex + 1 + resetAllExtra -> BuiltinFolderRow(
            folder = BuiltinFolder.SAVE,
            platformId = platformId,
            resettable = if (isGlobal) videoState.isCustomSavePath else platformSettings?.savePath != null
        )
        maxSettingsIndex + 2 + resetAllExtra -> BuiltinFolderRow(
            folder = BuiltinFolder.STATE,
            platformId = platformId,
            resettable = if (isGlobal) videoState.isCustomStatePath else platformSettings?.statePath != null
        )
        else -> null
    }
}

internal class BuiltinVideoSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycleSettings(-1)

    override fun onRight(): InputResult = cycleSettings(1)

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        val row = focusedBuiltinFolderRow(state) ?: return resetFocusedSettingOverride()
        if (!row.resettable) return InputResult.HANDLED
        val platformId = row.platformId
        when (row.folder) {
            BuiltinFolder.SAVE -> if (platformId == null) {
                viewModel.resetBuiltinSavePath()
            } else {
                viewModel.resetPlatformBuiltinSavePath(platformId)
            }
            BuiltinFolder.STATE -> if (platformId == null) {
                viewModel.resetBuiltinStatePath()
            } else {
                viewModel.resetPlatformBuiltinStatePath(platformId)
            }
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val videoState = state.builtinVideo
        val isGlobal = videoState.isGlobalContext
        val platformContext = videoState.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val hasAnyOverrides = platformSettings?.hasAnyOverrides() == true

        val maxSettingsIndex = libretroSettingsMaxFocusIndex(
            platformSlug = platformContext?.platformSlug,
            canEnableBFI = videoState.canEnableBlackFrameInsertion
        )

        val resetAllIndex = maxSettingsIndex + 1
        if (!isGlobal && hasAnyOverrides && state.focusedIndex == resetAllIndex) {
            viewModel.resetAllPlatformLibretroSettings()
            return InputResult.HANDLED
        }

        if (videoState.savePath.isNotEmpty()) {
            val resetAllExtra = if (!isGlobal && hasAnyOverrides) 1 else 0
            val savePathIndex = maxSettingsIndex + 1 + resetAllExtra
            val statePathIndex = maxSettingsIndex + 2 + resetAllExtra
            when (state.focusedIndex) {
                savePathIndex -> {
                    if (isGlobal) viewModel.openBuiltinSavePathBrowser()
                    else platformContext?.let { viewModel.openPlatformBuiltinSavePathBrowser(it.platformId) }
                    return InputResult.HANDLED
                }
                statePathIndex -> {
                    if (isGlobal) viewModel.openBuiltinStatePathBrowser()
                    else platformContext?.let { viewModel.openPlatformBuiltinStatePathBrowser(it.platformId) }
                    return InputResult.HANDLED
                }
            }
        }

        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        if (isGlobal) {
            return confirmGlobal(setting, videoState)
        }
        return confirmPlatform(setting, videoState, platformSettings)
    }

    private fun confirmGlobal(
        setting: LibretroSettingDef,
        videoState: com.nendo.argosy.ui.screens.settings.BuiltinVideoState
    ): InputResult = when (setting) {
        LibretroSettingDef.Shader,
        LibretroSettingDef.AspectRatio,
        LibretroSettingDef.Rotation,
        LibretroSettingDef.OverscanCrop,
        LibretroSettingDef.AudioVolume -> {
            viewModel.requestEnumPicker(setting.key)
            InputResult.handled(SoundType.OPEN_MODAL)
        }
        LibretroSettingDef.PortraitPosition,
        LibretroSettingDef.FastForwardSpeed,
        LibretroSettingDef.RewindSpeed,
        LibretroSettingDef.RewindBufferDuration -> InputResult.handled(SoundType.SILENT)
        LibretroSettingDef.Filter -> {
            if (videoState.shader == "Custom") {
                viewModel.openShaderChainConfig()
                InputResult.HANDLED
            } else {
                InputResult.handled(SoundType.SILENT)
            }
        }
        LibretroSettingDef.FastForwardEnabled -> {
            viewModel.setBuiltinFastForwardEnabled(!videoState.fastForwardEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.BlackFrameInsertion -> {
            viewModel.setBuiltinBlackFrameInsertion(!videoState.blackFrameInsertion)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.RewindEnabled -> {
            viewModel.setBuiltinRewindEnabled(!videoState.rewindEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.SkipDuplicateFrames -> {
            viewModel.setBuiltinSkipDuplicateFrames(!videoState.skipDuplicateFrames)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.LowLatencyAudio -> {
            viewModel.setBuiltinLowLatencyAudio(!videoState.lowLatencyAudio)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.VSync -> {
            viewModel.setBuiltinVSync(!videoState.vsync)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.Frame -> {
            viewModel.setBuiltinFramesEnabled(!videoState.framesEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.AutoSaveState -> {
            viewModel.setBuiltinAutoSaveState(!videoState.autoSaveState)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.AutoRestoreState -> {
            viewModel.setBuiltinAutoRestoreState(!videoState.autoRestoreState)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.PreferNewerServerSave -> {
            viewModel.setBuiltinPreferNewerServerSave(!videoState.preferNewerServerSave)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.HwCoreSaveStates -> {
            viewModel.setBuiltinHwCoreSaveStates(!videoState.hwCoreSaveStatesEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
    }

    private fun confirmPlatform(
        setting: LibretroSettingDef,
        videoState: com.nendo.argosy.ui.screens.settings.BuiltinVideoState,
        platformSettings: com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity?
    ): InputResult {
        val accessor = PlatformLibretroSettingsAccessor(
            platformSettings = platformSettings,
            globalState = videoState,
            onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) },
            onActionCallback = { s ->
                when (s) {
                    LibretroSettingDef.Frame -> viewModel.openFrameConfig()
                    LibretroSettingDef.Filter -> viewModel.openShaderChainConfig()
                    else -> {}
                }
            }
        )
        if (accessor.isActionItem(setting)) {
            accessor.onAction(setting)
            return InputResult.HANDLED
        }
        return when (setting.type) {
            is LibretroSettingDef.SettingType.Cycle -> if (setting.isSegmented) {
                InputResult.handled(SoundType.SILENT)
            } else {
                viewModel.requestEnumPicker(setting.key)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            LibretroSettingDef.SettingType.Switch -> { accessor.toggle(setting); InputResult.handled(SoundType.TOGGLE) }
        }
    }

    private fun resetFocusedSettingOverride(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.isGlobalContext) return InputResult.UNHANDLED
        val videoState = state.builtinVideo
        val platformContext = videoState.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        val accessor = PlatformLibretroSettingsAccessor(
            platformSettings = platformSettings,
            globalState = videoState,
            onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
        )
        if (accessor.hasOverride(setting)) {
            accessor.reset(setting)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
            viewModel.cyclePlatformContext(-1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
            viewModel.cyclePlatformContext(1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycleSettings(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, state.builtinVideo) ?: return InputResult.UNHANDLED
        if (setting.type !is LibretroSettingDef.SettingType.Cycle) return InputResult.UNHANDLED
        if (readerFor(state).isAtSegmentEnd(setting, direction)) return InputResult.handled(SoundType.BOUNDARY)
        if (state.builtinVideo.isGlobalContext) {
            return cycleGlobal(setting, state, direction)
        }
        return cyclePlatform(setting, state, direction)
    }

    private fun readerFor(state: SettingsUiState): LibretroSettingsAccessor {
        val videoState = state.builtinVideo
        if (videoState.isGlobalContext) {
            return GlobalLibretroSettingsAccessor(videoState, onCycle = { _, _ -> }, onToggle = { _, _ -> })
        }
        val platformSettings = videoState.currentPlatformContext
            ?.let { state.platformLibretro.platformSettings[it.platformId] }
        return PlatformLibretroSettingsAccessor(platformSettings, videoState, onUpdate = { _, _ -> })
    }

    private fun cycleGlobal(
        setting: LibretroSettingDef,
        state: com.nendo.argosy.ui.screens.settings.SettingsUiState,
        direction: Int
    ): InputResult = when (setting) {
        LibretroSettingDef.Shader -> { viewModel.cycleBuiltinShader(direction); InputResult.HANDLED }
        LibretroSettingDef.Filter -> {
            if (state.builtinVideo.shader != "Custom") viewModel.cycleBuiltinFilter(direction)
            InputResult.HANDLED
        }
        LibretroSettingDef.AspectRatio -> { viewModel.cycleBuiltinAspectRatio(direction); InputResult.HANDLED }
        LibretroSettingDef.PortraitPosition -> { viewModel.cycleBuiltinPortraitPosition(direction); InputResult.HANDLED }
        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(direction); InputResult.HANDLED }
        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(direction); InputResult.HANDLED }
        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(direction); InputResult.HANDLED }
        LibretroSettingDef.AudioVolume -> { viewModel.cycleBuiltinAudioVolume(direction); InputResult.HANDLED }
        LibretroSettingDef.RewindSpeed -> { viewModel.cycleBuiltinRewindSpeed(direction); InputResult.HANDLED }
        LibretroSettingDef.RewindBufferDuration -> { viewModel.cycleBuiltinRewindBufferDuration(direction); InputResult.HANDLED }
        else -> InputResult.UNHANDLED
    }

    private fun cyclePlatform(
        setting: LibretroSettingDef,
        state: com.nendo.argosy.ui.screens.settings.SettingsUiState,
        direction: Int
    ): InputResult {
        val platformContext = state.builtinVideo.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val accessor = PlatformLibretroSettingsAccessor(
            platformSettings = platformSettings,
            globalState = state.builtinVideo,
            onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
        )
        if (!accessor.isActionItem(setting)) {
            accessor.cycle(setting, direction)
        }
        return InputResult.HANDLED
    }
}
