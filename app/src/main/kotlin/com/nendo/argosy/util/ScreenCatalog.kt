package com.nendo.argosy.util

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AttachedScreen(
    val key: String,
    val displayId: Int,
    val number: Int,
    val widthPx: Int,
    val heightPx: Int,
    val builtIn: Boolean
)

@Singleton
class ScreenCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun attachedScreens(): List<AttachedScreen> = displayManager.displays
        .filter { it.isUsablePhysicalDisplay() }
        .sortedBy { it.displayId }
        .mapIndexed { index, display ->
            val size = panelSizeOf(context, display)
            AttachedScreen(
                key = display.stableKey(size),
                displayId = display.displayId,
                number = index + 1,
                widthPx = size.x,
                heightPx = size.y,
                builtIn = display.isBuiltIn()
            )
        }

    fun screenFor(displayId: Int): AttachedScreen? =
        attachedScreens().find { it.displayId == displayId }

    /**
     * The lit screen a console's second screen belongs on while the game runs on [gameDisplayId],
     * or null when the game has the only one.
     */
    fun secondScreenFor(gameDisplayId: Int): Display? {
        val lit = attachedScreens().filter {
            displayManager.getDisplay(it.displayId)?.state?.let { state -> state != Display.STATE_OFF } == true
        }
        return chooseSecondScreen(lit, gameDisplayId)?.let { displayManager.getDisplay(it.displayId) }
    }

    companion object {
        internal fun chooseSecondScreen(screens: List<AttachedScreen>, gameDisplayId: Int): AttachedScreen? =
            screens
                .filter { it.displayId != gameDisplayId }
                .minWithOrNull(
                    compareBy<AttachedScreen> { !it.builtIn }
                        .thenBy { it.widthPx.toLong() * it.heightPx }
                        .thenBy { it.displayId }
                )

        private const val DISPLAY_TYPE_BUILT_IN = 1
        private const val DISPLAY_TYPE_EXTERNAL = 2

        private fun Display.displayType(): Int? = try {
            Display::class.java.getMethod("getType").invoke(this) as? Int
        } catch (_: Exception) { null }

        private fun Display.uniqueIdOrNull(): String? = try {
            (Display::class.java.getMethod("getUniqueId").invoke(this) as? String)?.takeIf {
                it.isNotBlank()
            }
        } catch (_: Exception) { null }

        fun panelSizeOf(context: Context, display: Display): Point {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bounds = runCatching {
                    context.createDisplayContext(display)
                        .createWindowContext(WINDOW_CONTEXT_TYPE, null)
                        .getSystemService(android.view.WindowManager::class.java)
                        ?.maximumWindowMetrics
                        ?.bounds
                }.getOrNull()
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    return Point(bounds.width(), bounds.height())
                }
            }
            return Point().also {
                @Suppress("DEPRECATION")
                display.getRealSize(it)
            }
        }

        private const val WINDOW_CONTEXT_TYPE =
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        private fun Display.stableKey(size: Point): String {
            uniqueIdOrNull()?.let { return it }
            return fallbackScreenKey(displayId, name, size.x, size.y, isBuiltIn())
        }

        /**
         * A screen's key when the platform withholds its unique id. A built-in panel keeps its
         * display id, which never changes; an external one is named by its name and size, since
         * Android hands it a new display id on every hotplug.
         */
        internal fun fallbackScreenKey(
            displayId: Int,
            name: String?,
            widthPx: Int,
            heightPx: Int,
            builtIn: Boolean
        ): String {
            val short = minOf(widthPx, heightPx)
            val long = maxOf(widthPx, heightPx)
            return if (builtIn) {
                "display:$displayId:${short}x$long"
            } else {
                "external:${name.orEmpty().ifBlank { "screen" }}:${short}x$long"
            }
        }

        private fun Display.isBuiltIn(): Boolean {
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN
            return flags and Display.FLAG_PRESENTATION == 0
        }

        private fun Display.isUsablePhysicalDisplay(): Boolean {
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN || type == DISPLAY_TYPE_EXTERNAL
            return flags and Display.FLAG_PRIVATE == 0
        }
    }
}
