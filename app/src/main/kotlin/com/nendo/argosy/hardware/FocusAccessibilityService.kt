package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FocusAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    @SuppressLint("NewApi")
    fun tapOnDisplay(displayId: Int) {
        val path = Path().apply { moveTo(1f, 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Focus tap completed on display $displayId")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Focus tap cancelled on display $displayId")
            }
        }, null)
        Log.d(TAG, "Focus tap dispatched=$dispatched on display $displayId")
    }

    /**
     * A silent capture of [displayId], with no flash, sound or consent prompt, as a software
     * bitmap; null before Android 11 or when the system refuses, such as within its rate limit.
     */
    suspend fun captureDisplay(displayId: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(displayId, Dispatchers.IO.asExecutor(), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = runCatching {
                        screenshot.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { wrapped ->
                                wrapped.copy(Bitmap.Config.ARGB_8888, false).also { wrapped.recycle() }
                            }
                        }
                    }.onFailure { Log.w(TAG, "Screenshot of display $displayId unreadable", it) }.getOrNull()
                    continuation.resume(bitmap) { bitmap?.recycle() }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot of display $displayId refused: $errorCode")
                    continuation.resume(null)
                }
            })
        }
    }

    companion object {
        private const val TAG = "FocusA11y"
        var instance: FocusAccessibilityService? = null
            private set
    }
}
