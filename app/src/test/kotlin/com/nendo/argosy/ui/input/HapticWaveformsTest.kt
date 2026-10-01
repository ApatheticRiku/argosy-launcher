package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticWaveformsTest {

    private fun waveform(pattern: HapticPattern, strength: Float = 1f): HapticWaveform =
        requireNotNull(HapticWaveforms.forPattern(pattern, strength))

    private fun pulses(waveform: HapticWaveform): List<Int> = waveform.amplitudes.filter { it > 0 }

    private fun peak(pattern: HapticPattern, strength: Float = 1f): Int =
        waveform(pattern, strength).amplitudes.max()

    @Test
    fun `zero strength silences every pattern`() {
        HapticPattern.entries.forEach { pattern ->
            assertNull(pattern.name, HapticWaveforms.forPattern(pattern, 0f))
        }
    }

    @Test
    fun `negative strength is treated as zero and above one as full`() {
        assertNull(HapticWaveforms.forPattern(HapticPattern.SELECTION, -0.5f))
        assertEquals(waveform(HapticPattern.SELECTION, 1f), waveform(HapticPattern.SELECTION, 3f))
    }

    @Test
    fun `timings and amplitudes line up and amplitudes stay in range at any strength`() {
        listOf(0.01f, 0.1f, 0.5f, 1f).forEach { strength ->
            HapticPattern.entries.forEach { pattern ->
                val shape = waveform(pattern, strength)
                assertEquals(pattern.name, shape.timingsMs.size, shape.amplitudes.size)
                assertTrue(pattern.name, shape.timingsMs.all { it > 0 })
                assertTrue(pattern.name, shape.amplitudes.all { it in 0..255 })
                assertTrue(pattern.name, pulses(shape).all { it >= 1 })
            }
        }
    }

    @Test
    fun `amplitude rises with strength and pauses stay silent`() {
        HapticPattern.entries.forEach { pattern ->
            val low = waveform(pattern, 0.2f)
            val high = waveform(pattern, 0.8f)
            low.amplitudes.zip(high.amplitudes).forEach { (l, h) ->
                if (l == 0) assertEquals(pattern.name, 0, h) else assertTrue(pattern.name, h > l)
            }
        }
    }

    @Test
    fun `focus change is the shortest and softest input cue`() {
        val focus = waveform(HapticPattern.FOCUS_CHANGE)
        assertTrue(focus.durationMs in 12L..15L)
        listOf(HapticPattern.SELECTION, HapticPattern.BOUNDARY_HIT, HapticPattern.ERROR).forEach {
            assertTrue(it.name, waveform(it).durationMs > focus.durationMs)
            assertTrue(it.name, peak(it) > peak(HapticPattern.FOCUS_CHANGE))
        }
    }

    @Test
    fun `back is lighter than confirm`() {
        assertTrue(peak(HapticPattern.BACK) < peak(HapticPattern.SELECTION))
    }

    @Test
    fun `open rises from a soft tap to a firm tap`() {
        val taps = pulses(waveform(HapticPattern.OPEN))
        assertEquals(2, taps.size)
        assertTrue(taps[0] < taps[1])
    }

    @Test
    fun `toggle on is firm then soft and toggle off is soft then firm`() {
        val on = pulses(waveform(HapticPattern.TOGGLE_ON))
        val off = pulses(waveform(HapticPattern.TOGGLE_OFF))
        assertEquals(2, on.size)
        assertEquals(2, off.size)
        assertTrue(on[0] > on[1])
        assertTrue(off[0] < off[1])
    }

    @Test
    fun `download start is two light taps and completion is three taps`() {
        val start = waveform(HapticPattern.DOWNLOAD_START)
        val complete = waveform(HapticPattern.DOWNLOAD_COMPLETE)
        assertEquals(2, pulses(start).size)
        assertEquals(3, pulses(complete).size)
        assertTrue(start.amplitudes.contains(0))
        assertTrue(pulses(start).all { it < peak(HapticPattern.SELECTION) })
    }

    @Test
    fun `launch is one continuous press ramping up over about 120 ms`() {
        val launch = waveform(HapticPattern.LAUNCH_GAME)
        assertTrue(launch.durationMs in 100L..140L)
        assertFalse(launch.amplitudes.contains(0))
        assertEquals(launch.amplitudes.sorted(), launch.amplitudes)
        assertTrue(launch.amplitudes.first() < launch.amplitudes.last())
    }

    @Test
    fun `error is two heavy taps`() {
        val taps = pulses(waveform(HapticPattern.ERROR))
        assertEquals(2, taps.size)
        assertTrue(taps.all { it >= peak(HapticPattern.SELECTION) })
    }

    @Test
    fun `boundary hit is a single heavy bump`() {
        val bump = waveform(HapticPattern.BOUNDARY_HIT)
        assertFalse(bump.amplitudes.contains(0))
        assertTrue(peak(HapticPattern.BOUNDARY_HIT) > peak(HapticPattern.SELECTION))
    }

    @Test
    fun `strength preview plays at the chosen strength`() {
        assertEquals(255, peak(HapticPattern.STRENGTH_PREVIEW, 1f))
        assertEquals(128, peak(HapticPattern.STRENGTH_PREVIEW, 0.5f))
        assertEquals(26, peak(HapticPattern.STRENGTH_PREVIEW, 0.1f))
    }

    @Test
    fun `a lower priority cue cannot cut off an unfinished higher priority cue`() {
        assertFalse(
            HapticWaveforms.canInterrupt(HapticPattern.OPEN, 100L, HapticPattern.SELECTION, 50L)
        )
        assertTrue(
            HapticWaveforms.canInterrupt(HapticPattern.OPEN, 100L, HapticPattern.SELECTION, 100L)
        )
    }

    @Test
    fun `an equal or higher priority cue replaces the active cue`() {
        assertTrue(
            HapticWaveforms.canInterrupt(HapticPattern.FOCUS_CHANGE, 100L, HapticPattern.FOCUS_CHANGE, 50L)
        )
        assertTrue(
            HapticWaveforms.canInterrupt(HapticPattern.SELECTION, 100L, HapticPattern.LAUNCH_GAME, 50L)
        )
        assertTrue(HapticWaveforms.canInterrupt(null, 0L, HapticPattern.FOCUS_CHANGE, 0L))
    }

    @Test
    fun `every audible sound type carries a haptic cue`() {
        val silent = setOf(SoundType.SILENT, SoundType.VOLUME_PREVIEW)
        SoundType.entries.forEach { sound ->
            if (sound in silent) assertNull(sound.name, sound.hapticCue)
            else assertNotNull(sound.name, sound.hapticCue)
        }
    }

    @Test
    fun `sound types map to their cue`() {
        assertEquals(HapticPattern.FOCUS_CHANGE, SoundType.NAVIGATE.hapticCue)
        assertEquals(HapticPattern.BOUNDARY_HIT, SoundType.BOUNDARY.hapticCue)
        assertEquals(HapticPattern.SELECTION, SoundType.SELECT.hapticCue)
        assertEquals(HapticPattern.BACK, SoundType.BACK.hapticCue)
        assertEquals(HapticPattern.BACK, SoundType.CLOSE_MODAL.hapticCue)
        assertEquals(HapticPattern.OPEN, SoundType.OPEN_MODAL.hapticCue)
        assertEquals(HapticPattern.DOWNLOAD_START, SoundType.DOWNLOAD_START.hapticCue)
        assertEquals(HapticPattern.DOWNLOAD_COMPLETE, SoundType.DOWNLOAD_COMPLETE.hapticCue)
        assertEquals(HapticPattern.LAUNCH_GAME, SoundType.LAUNCH_GAME.hapticCue)
        assertEquals(HapticPattern.ERROR, SoundType.ERROR.hapticCue)
    }
}
