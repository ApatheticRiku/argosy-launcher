package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType

val SoundType.hapticCue: HapticPattern?
    get() = when (this) {
        SoundType.NAVIGATE, SoundType.SECTION_CHANGE -> HapticPattern.FOCUS_CHANGE
        SoundType.BOUNDARY -> HapticPattern.BOUNDARY_HIT
        SoundType.SELECT, SoundType.TOGGLE -> HapticPattern.SELECTION
        SoundType.BACK, SoundType.CLOSE_MODAL, SoundType.DOWNLOAD_CANCEL -> HapticPattern.BACK
        SoundType.OPEN_MODAL -> HapticPattern.OPEN
        SoundType.FAVORITE -> HapticPattern.TOGGLE_ON
        SoundType.UNFAVORITE -> HapticPattern.TOGGLE_OFF
        SoundType.DOWNLOAD_START -> HapticPattern.DOWNLOAD_START
        SoundType.DOWNLOAD_COMPLETE -> HapticPattern.DOWNLOAD_COMPLETE
        SoundType.ERROR -> HapticPattern.ERROR
        SoundType.LAUNCH_GAME -> HapticPattern.LAUNCH_GAME
        SoundType.SILENT, SoundType.VOLUME_PREVIEW -> null
    }
