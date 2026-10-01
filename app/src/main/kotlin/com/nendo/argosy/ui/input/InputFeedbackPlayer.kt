package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType

/**
 * Plays the haptic cue and sound for a handled gamepad input, on either display. A held direction
 * against the end of a list plays its boundary cue once, then stays silent until focus moves.
 */
class InputFeedbackPlayer(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {

    private var boundaryLatched = false

    fun play(event: GamepadEvent, result: InputResult) {
        if (!result.handled) return

        when (event) {
            GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right ->
                playMove(result, SoundType.NAVIGATE)
            GamepadEvent.PrevSection, GamepadEvent.NextSection,
            GamepadEvent.PrevTrigger, GamepadEvent.NextTrigger ->
                playMove(result, SoundType.SECTION_CHANGE)
            GamepadEvent.Confirm, GamepadEvent.LongConfirm -> {
                val sound = result.soundOverride ?: SoundType.SELECT
                vibrate(result.hapticOverride ?: sound.hapticCue ?: HapticPattern.SELECTION)
                soundManager?.playSound(sound)
            }
            GamepadEvent.Back -> {
                val sound = result.soundOverride ?: SoundType.BACK
                vibrate(result.hapticOverride ?: sound.hapticCue)
                soundManager?.playSound(sound)
            }
            else -> vibrate(result.hapticOverride ?: result.soundOverride?.hapticCue)
        }
    }

    private fun playMove(result: InputResult, defaultSound: SoundType) {
        val sound = latchBoundary(result.soundOverride) ?: defaultSound
        val cue = when {
            result.hapticOverride != null -> result.hapticOverride
            sound == SoundType.SILENT -> null
            else -> sound.hapticCue ?: HapticPattern.FOCUS_CHANGE
        }
        vibrate(cue)
        soundManager?.playSound(sound)
    }

    private fun vibrate(pattern: HapticPattern?) {
        if (pattern != null) hapticManager?.vibrate(pattern)
    }

    private fun latchBoundary(override: SoundType?): SoundType? {
        if (override != SoundType.BOUNDARY) {
            boundaryLatched = false
            return override
        }
        if (boundaryLatched) return SoundType.SILENT
        boundaryLatched = true
        return override
    }
}
