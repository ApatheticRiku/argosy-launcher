package com.nendo.argosy.ui.input

import kotlin.math.roundToInt

/**
 * Segments played back to back. A segment with amplitude 0 is a pause; every other amplitude is
 * in 1..255.
 */
data class HapticWaveform(
    val timingsMs: List<Long>,
    val amplitudes: List<Int>
) {
    val durationMs: Long get() = timingsMs.sum()
}

object HapticWaveforms {
    private const val MAX_AMPLITUDE = 255
    private const val PRIORITY_AMBIENT = 0
    private const val PRIORITY_CUE = 1
    private const val PRIORITY_ALERT = 2

    private data class Segment(val durationMs: Long, val baseAmplitude: Int)

    private data class Shape(val priority: Int, val segments: List<Segment>)

    private fun pulse(durationMs: Long, baseAmplitude: Int) = Segment(durationMs, baseAmplitude)

    private fun pause(durationMs: Long) = Segment(durationMs, 0)

    private fun shapeOf(pattern: HapticPattern): Shape = when (pattern) {
        HapticPattern.FOCUS_CHANGE -> Shape(PRIORITY_AMBIENT, listOf(pulse(14, 140)))
        HapticPattern.SELECTION -> Shape(PRIORITY_AMBIENT, listOf(pulse(22, 215)))
        HapticPattern.BACK -> Shape(PRIORITY_AMBIENT, listOf(pulse(16, 125)))
        HapticPattern.OPEN -> Shape(
            PRIORITY_CUE,
            listOf(pulse(16, 115), pause(35), pulse(26, 235))
        )
        HapticPattern.TOGGLE_ON -> Shape(
            PRIORITY_CUE,
            listOf(pulse(24, 230), pause(55), pulse(16, 115))
        )
        HapticPattern.TOGGLE_OFF -> Shape(
            PRIORITY_CUE,
            listOf(pulse(16, 115), pause(55), pulse(24, 230))
        )
        HapticPattern.BOUNDARY_HIT -> Shape(
            PRIORITY_CUE,
            listOf(pulse(20, 255), pulse(30, 170))
        )
        HapticPattern.DOWNLOAD_START -> Shape(
            PRIORITY_CUE,
            listOf(pulse(14, 130), pause(45), pulse(14, 130))
        )
        HapticPattern.DOWNLOAD_COMPLETE -> Shape(
            PRIORITY_ALERT,
            listOf(pulse(18, 200), pause(45), pulse(18, 200), pause(45), pulse(18, 200))
        )
        HapticPattern.LAUNCH_GAME -> Shape(
            PRIORITY_ALERT,
            listOf(pulse(30, 90), pulse(30, 150), pulse(30, 210), pulse(30, 255))
        )
        HapticPattern.ERROR -> Shape(
            PRIORITY_ALERT,
            listOf(pulse(35, 255), pause(60), pulse(35, 255))
        )
        HapticPattern.STRENGTH_PREVIEW -> Shape(PRIORITY_ALERT, listOf(pulse(45, 255)))
    }

    fun forPattern(pattern: HapticPattern, strength: Float): HapticWaveform? {
        val clamped = strength.coerceIn(0f, 1f)
        if (clamped == 0f) return null
        val segments = shapeOf(pattern).segments
        return HapticWaveform(
            timingsMs = segments.map { it.durationMs },
            amplitudes = segments.map { scaledAmplitude(it.baseAmplitude, clamped) }
        )
    }

    /**
     * Whether [candidate] may replace [active], which plays until [activeUntilMs]. An unfinished
     * cue yields only to a cue of equal or higher priority.
     */
    fun canInterrupt(
        active: HapticPattern?,
        activeUntilMs: Long,
        candidate: HapticPattern,
        nowMs: Long
    ): Boolean =
        active == null ||
            nowMs >= activeUntilMs ||
            shapeOf(candidate).priority >= shapeOf(active).priority

    private fun scaledAmplitude(baseAmplitude: Int, strength: Float): Int =
        if (baseAmplitude == 0) {
            0
        } else {
            (baseAmplitude * strength).roundToInt().coerceIn(1, MAX_AMPLITUDE)
        }
}
