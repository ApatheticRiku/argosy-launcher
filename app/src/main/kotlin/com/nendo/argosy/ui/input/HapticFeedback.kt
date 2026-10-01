package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.nendo.argosy.util.PServerExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SELECTION,
    BOUNDARY_HIT,
    ERROR,
    STRENGTH_PREVIEW
}

@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DEFAULT_STRENGTH = 0.5f
        private const val FOCUS_SCALE = 0.6f
        private const val MIN_SCALE = 0.05f
        private const val ERROR_GAP_MS = 80
        private const val PREVIEW_GAP_MS = 140
        private const val FALLBACK_PULSE_MS = 20L
        private const val FALLBACK_HEAVY_PULSE_MS = 35L
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private var enabled = true

    @Volatile
    private var cachedStrength: Float? = null

    private val supportsPrimitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD
            ) == true

    private val touchAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        } else {
            null
        }

    val supportsSystemVibration: Boolean
        get() = PServerExecutor.isAvailable

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Reads the live system setting, which costs a binder transaction and a shell command.
     * Call it to display or re-sync the value; [vibrate] uses the cached strength so a
     * d-pad repeat does not issue one IPC per tick.
     */
    fun getSystemVibrationStrength(): Float {
        val strength = PServerExecutor.getSystemSettingFloat("vibrate_strength_value", DEFAULT_STRENGTH)
        cachedStrength = strength
        return strength
    }

    fun setSystemVibrationStrength(strength: Float): Boolean {
        val clamped = strength.coerceIn(0f, 1f)
        val applied = PServerExecutor.setSystemSettingFloat("vibrate_strength_value", clamped)
        if (applied) cachedStrength = clamped
        return applied
    }

    private fun currentStrength(): Float = cachedStrength ?: getSystemVibrationStrength()

    fun vibrate(pattern: HapticPattern) {
        val vibrator = vibrator ?: return
        if (!enabled || !vibrator.hasVibrator()) return
        val effect = when {
            supportsPrimitives -> composedEffect(pattern)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> predefinedEffect(pattern)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> pulseEffect(pattern)
            else -> null
        }
        when {
            effect == null -> legacyPulse(vibrator, pattern)
            touchAttributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                vibrator.vibrate(effect, touchAttributes)
            else -> vibrator.vibrate(effect)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composedEffect(pattern: HapticPattern): VibrationEffect {
        val strength = currentStrength().coerceIn(MIN_SCALE, 1f)
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val thud = VibrationEffect.Composition.PRIMITIVE_THUD
        val composition = VibrationEffect.startComposition()
        when (pattern) {
            HapticPattern.FOCUS_CHANGE -> composition.addPrimitive(click, (strength * FOCUS_SCALE).coerceAtLeast(MIN_SCALE))
            HapticPattern.SELECTION -> composition.addPrimitive(click, strength)
            HapticPattern.BOUNDARY_HIT -> composition.addPrimitive(thud, strength)
            HapticPattern.ERROR -> composition
                .addPrimitive(click, strength)
                .addPrimitive(click, strength, ERROR_GAP_MS)
            HapticPattern.STRENGTH_PREVIEW -> composition
                .addPrimitive(click, strength)
                .addPrimitive(click, strength, PREVIEW_GAP_MS)
                .addPrimitive(click, strength, PREVIEW_GAP_MS)
        }
        return composition.compose()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefinedEffect(pattern: HapticPattern): VibrationEffect = VibrationEffect.createPredefined(
        when (pattern) {
            HapticPattern.FOCUS_CHANGE -> VibrationEffect.EFFECT_TICK
            HapticPattern.SELECTION -> VibrationEffect.EFFECT_CLICK
            HapticPattern.BOUNDARY_HIT -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticPattern.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
            HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.EFFECT_CLICK
        }
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pulseEffect(pattern: HapticPattern): VibrationEffect {
        val amplitude = (currentStrength() * 255).toInt().coerceIn(1, 255)
        val duration = when (pattern) {
            HapticPattern.BOUNDARY_HIT, HapticPattern.ERROR -> FALLBACK_HEAVY_PULSE_MS
            else -> FALLBACK_PULSE_MS
        }
        return VibrationEffect.createOneShot(duration, amplitude)
    }

    @Suppress("DEPRECATION")
    private fun legacyPulse(vibrator: Vibrator, pattern: HapticPattern) {
        vibrator.vibrate(
            when (pattern) {
                HapticPattern.BOUNDARY_HIT, HapticPattern.ERROR -> FALLBACK_HEAVY_PULSE_MS
                else -> FALLBACK_PULSE_MS
            }
        )
    }
}
