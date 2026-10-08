package com.nendo.argosy.data.wallpaper

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LockScreenWallpaperService : WallpaperService() {

    @Inject lateinit var scenes: LockScreenScenes
    @Inject lateinit var artManager: LockScreenArtManager

    override fun onCreateEngine(): Engine = MosaicEngine()

    private inner class MosaicEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private var scene: LockScreenScene? = null
        private var visible = false
        private var animating = false
        private var startedAt = SystemClock.uptimeMillis()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            scope.launch {
                scenes.scene.collect {
                    scene = it
                    startedAt = SystemClock.uptimeMillis()
                    update()
                }
            }
            if (isPreview) artManager.prepareLivePreview() else artManager.onLiveWallpaperBound()
        }

        override fun onDestroy() {
            stop()
            scope.cancel()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            update()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            update()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun doFrame(frameTimeNanos: Long) {
            animating = false
            draw()
            if (shouldAnimate()) start()
        }

        private fun update() {
            draw()
            if (shouldAnimate()) start() else stop()
        }

        private fun shouldAnimate(): Boolean =
            visible && scene is LockScreenScene.Mosaic && ValueAnimator.areAnimatorsEnabled() &&
                (isPreview || keyguard?.isKeyguardLocked == true)

        private fun start() {
            if (animating) return
            animating = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun stop() {
            if (!animating) return
            animating = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                render(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            when (val current = scene) {
                null -> Unit
                is LockScreenScene.Hero -> canvas.drawBitmap(current.bitmap, 0f, 0f, paint)
                is LockScreenScene.Mosaic -> {
                    val seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f
                    for (row in current.rows) {
                        var x = -((seconds * row.pixelsPerSecond) % row.period)
                        while (x < canvas.width) {
                            canvas.drawBitmap(row.strip, x, row.top, paint)
                            x += row.period
                        }
                    }
                    canvas.drawColor(Color.argb(current.dimAlpha, 0, 0, 0))
                }
            }
        }
    }
}
