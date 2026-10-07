package com.nendo.argosy.hardware

import android.os.Build
import android.view.Display
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.ScreenCatalog
import com.nendo.argosy.util.SystemSettings
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayBrightness(
    val primary: Float?,
    val secondary: Float?
)

@Singleton
class BrightnessController @Inject constructor(
    private val systemSettings: SystemSettings,
    private val screenCatalog: ScreenCatalog
) {
    fun getBrightness(): DisplayBrightness =
        DisplayBrightness(primary = systemSettings.screenBrightness(), secondary = null)

    fun setPrimaryBrightness(brightness: Float): Boolean =
        systemSettings.setScreenBrightness(brightness)

    /**
     * The second built-in panel's brightness, read through the root route. Null on a device with
     * one panel, without a root route, or on an Android release whose display service calls are
     * not mapped. Blocks on a shell call.
     */
    fun secondaryBrightness(): Float? {
        val call = displayCalls ?: return null
        if (!RootShell.isAvailable) return null
        val displayId = secondaryPanelId() ?: return null
        val reply = RootShell.execute("service call display ${call.get} i32 $displayId").getOrNull()
        return parcelFloat(reply)
    }

    /**
     * Sets the second built-in panel's brightness, 0 to 1. Blocks on a shell call.
     */
    fun setSecondaryBrightness(brightness: Float): Boolean {
        val call = displayCalls ?: return false
        if (!RootShell.isAvailable) return false
        val displayId = secondaryPanelId() ?: return false
        val value = brightness.coerceIn(0f, 1f)
        return RootShell.execute("service call display ${call.set} i32 $displayId f $value").isSuccess
    }

    private fun secondaryPanelId(): Int? = screenCatalog.attachedScreens()
        .firstOrNull { it.builtIn && it.displayId != Display.DEFAULT_DISPLAY }
        ?.displayId

    internal class DisplayServiceCalls(val set: Int, val get: Int)

    companion object {
        private val callsBySdk = mapOf(
            Build.VERSION_CODES.TIRAMISU to DisplayServiceCalls(set = 35, get = 36)
        )

        private val displayCalls: DisplayServiceCalls? get() = callsBySdk[Build.VERSION.SDK_INT]

        private val parcelWord = Regex("""Parcel\(\s*([0-9a-fA-F]{8})\s+([0-9a-fA-F]{8})""")

        internal fun parcelFloat(reply: String?): Float? {
            val match = reply?.let { parcelWord.find(it) } ?: return null
            if (match.groupValues[1].toLong(16) != 0L) return null
            return java.lang.Float.intBitsToFloat(match.groupValues[2].toLong(16).toInt())
        }
    }
}
