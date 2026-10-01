package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.nendo.argosy.data.preferences.ControlsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SELECTION,
    BACK,
    OPEN,
    TOGGLE_ON,
    TOGGLE_OFF,
    BOUNDARY_HIT,
    DOWNLOAD_START,
    DOWNLOAD_COMPLETE,
    LAUNCH_GAME,
    ERROR,
    STRENGTH_PREVIEW
}

@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private var enabled = true

    @Volatile
    var strength: Float = ControlsPreferences.DEFAULT_HAPTIC_STRENGTH
        private set

    @Volatile
    private var activePattern: HapticPattern? = null

    @Volatile
    private var activeUntilMs = 0L

    private val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true

    private val touchAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        } else {
            null
        }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setStrength(strength: Float) {
        this.strength = strength.coerceIn(0f, 1f)
    }

    fun vibrate(pattern: HapticPattern) {
        val vibrator = vibrator ?: return
        if (!enabled || !vibrator.hasVibrator()) return
        val waveform = HapticWaveforms.forPattern(pattern, strength) ?: return
        val now = SystemClock.uptimeMillis()
        if (!HapticWaveforms.canInterrupt(activePattern, activeUntilMs, pattern, now)) return
        activePattern = pattern
        activeUntilMs = now + waveform.durationMs
        val effect = when {
            hasAmplitudeControl -> waveformEffect(waveform)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> predefinedEffect(pattern)
            else -> waveformEffect(waveform.withDefaultAmplitude())
        }
        if (touchAttributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, touchAttributes)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun waveformEffect(waveform: HapticWaveform): VibrationEffect =
        if (waveform.timingsMs.size == 1) {
            VibrationEffect.createOneShot(waveform.timingsMs.first(), waveform.amplitudes.first())
        } else {
            VibrationEffect.createWaveform(
                waveform.timingsMs.toLongArray(),
                waveform.amplitudes.toIntArray(),
                -1
            )
        }

    private fun HapticWaveform.withDefaultAmplitude(): HapticWaveform =
        copy(amplitudes = amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 })

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefinedEffect(pattern: HapticPattern): VibrationEffect = VibrationEffect.createPredefined(
        when (pattern) {
            HapticPattern.FOCUS_CHANGE, HapticPattern.BACK, HapticPattern.TOGGLE_OFF -> VibrationEffect.EFFECT_TICK
            HapticPattern.SELECTION, HapticPattern.TOGGLE_ON, HapticPattern.STRENGTH_PREVIEW ->
                VibrationEffect.EFFECT_CLICK
            HapticPattern.BOUNDARY_HIT, HapticPattern.LAUNCH_GAME -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticPattern.OPEN, HapticPattern.DOWNLOAD_START, HapticPattern.DOWNLOAD_COMPLETE,
            HapticPattern.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }
    )
}
