package com.nendo.argosy.hardware

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class DisplayRefreshController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun supportedRatesHz(): List<Int> {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        val active = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == active.physicalWidth && it.physicalHeight == active.physicalHeight }
            .map { it.refreshRate.roundToInt() }
            .distinct()
            .sorted()
    }

    /**
     * The pinned rate in hertz, or null while the system picks the rate itself.
     */
    fun currentRateHz(): Int? =
        Settings.System.getString(context.contentResolver, KEY_PEAK)?.toFloatOrNull()?.roundToInt()

    /**
     * Pins both refresh bounds to [hz], or clears them when [hz] is null. Needs the root route.
     */
    fun setRateHz(hz: Int?): Boolean {
        val command = if (hz == null) {
            "settings delete system $KEY_MIN; settings delete system $KEY_PEAK"
        } else {
            "settings put system $KEY_MIN $hz; settings put system $KEY_PEAK $hz"
        }
        if (RootShell.execute(command).isFailure) return false
        return currentRateHz() == hz
    }

    private companion object {
        const val KEY_MIN = "min_refresh_rate"
        const val KEY_PEAK = "peak_refresh_rate"
    }
}
