package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class InputFeedbackPlayerTest {

    private val haptics = mockk<HapticFeedbackManager>(relaxed = true)
    private val sounds = mockk<SoundFeedbackManager>(relaxed = true)
    private val player = InputFeedbackPlayer(haptics, sounds)

    @Test
    fun `a plain move plays the focus tick`() {
        player.play(GamepadEvent.Down, InputResult.HANDLED)
        verify(exactly = 1) { haptics.vibrate(HapticPattern.FOCUS_CHANGE) }
        verify(exactly = 1) { sounds.playSound(SoundType.NAVIGATE) }
    }

    @Test
    fun `holding against a list edge bumps once`() {
        repeat(3) { player.play(GamepadEvent.Down, InputResult.handled(SoundType.BOUNDARY)) }
        verify(exactly = 1) { haptics.vibrate(HapticPattern.BOUNDARY_HIT) }
        verify(exactly = 0) { haptics.vibrate(HapticPattern.FOCUS_CHANGE) }
    }

    @Test
    fun `leaving the edge re-arms the bump`() {
        player.play(GamepadEvent.Down, InputResult.handled(SoundType.BOUNDARY))
        player.play(GamepadEvent.Up, InputResult.HANDLED)
        player.play(GamepadEvent.Down, InputResult.handled(SoundType.BOUNDARY))
        verifyOrder {
            haptics.vibrate(HapticPattern.BOUNDARY_HIT)
            haptics.vibrate(HapticPattern.FOCUS_CHANGE)
            haptics.vibrate(HapticPattern.BOUNDARY_HIT)
        }
    }

    @Test
    fun `a silent move plays no cue`() {
        player.play(GamepadEvent.Left, InputResult.handled(SoundType.SILENT))
        verify(exactly = 0) { haptics.vibrate(any()) }
    }

    @Test
    fun `a confirm plays the firm tap even when its sound is silent`() {
        player.play(GamepadEvent.Confirm, InputResult.HANDLED)
        player.play(GamepadEvent.Confirm, InputResult.handled(SoundType.SILENT))
        verify(exactly = 2) { haptics.vibrate(HapticPattern.SELECTION) }
    }

    @Test
    fun `a confirm that opens a modal plays the rising double`() {
        player.play(GamepadEvent.Confirm, InputResult.handled(SoundType.OPEN_MODAL))
        verify(exactly = 1) { haptics.vibrate(HapticPattern.OPEN) }
        verify(exactly = 0) { haptics.vibrate(HapticPattern.SELECTION) }
    }

    @Test
    fun `toggles play their direction on confirm and on a move`() {
        player.play(GamepadEvent.Confirm, InputResult.toggled(false, SoundType.SILENT))
        player.play(GamepadEvent.Right, InputResult.toggled(true))
        verifyOrder {
            haptics.vibrate(HapticPattern.TOGGLE_OFF)
            haptics.vibrate(HapticPattern.TOGGLE_ON)
        }
        verify(exactly = 1) { sounds.playSound(SoundType.SILENT) }
        verify(exactly = 1) { sounds.playSound(SoundType.TOGGLE) }
    }

    @Test
    fun `back plays the lighter tap and closing a modal does too`() {
        player.play(GamepadEvent.Back, InputResult.HANDLED)
        player.play(GamepadEvent.Back, InputResult.handled(SoundType.CLOSE_MODAL))
        verify(exactly = 2) { haptics.vibrate(HapticPattern.BACK) }
    }

    @Test
    fun `a silent back plays no cue`() {
        player.play(GamepadEvent.Back, InputResult.handled(SoundType.SILENT))
        verify(exactly = 0) { haptics.vibrate(any()) }
    }

    @Test
    fun `other buttons play a cue only when the handler names one`() {
        player.play(GamepadEvent.ContextMenu, InputResult.HANDLED)
        verify(exactly = 0) { haptics.vibrate(any()) }
        player.play(GamepadEvent.ContextMenu, InputResult.handled(SoundType.OPEN_MODAL))
        verify(exactly = 1) { haptics.vibrate(HapticPattern.OPEN) }
    }

    @Test
    fun `the player never plays the event cue through the sound manager`() {
        player.play(GamepadEvent.Confirm, InputResult.HANDLED)
        verify(exactly = 0) { sounds.play(any()) }
    }

    @Test
    fun `unhandled input plays nothing`() {
        player.play(GamepadEvent.Confirm, InputResult.UNHANDLED)
        verify(exactly = 0) { haptics.vibrate(any()) }
        verify(exactly = 0) { sounds.playSound(any()) }
    }
}
