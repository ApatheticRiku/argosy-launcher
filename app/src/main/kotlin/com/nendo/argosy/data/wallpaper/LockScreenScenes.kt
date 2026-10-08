package com.nendo.argosy.data.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockScreenScene {
    class Hero(val bitmap: Bitmap) : LockScreenScene
    class Mosaic(val rows: List<MosaicRow>, val dimAlpha: Int) : LockScreenScene
}

class MosaicRow(val strip: Bitmap, val top: Float, val period: Float, val pixelsPerSecond: Float)

@Singleton
class LockScreenScenes @Inject constructor() {
    private val _scene = MutableStateFlow<LockScreenScene?>(null)
    val scene: StateFlow<LockScreenScene?> = _scene.asStateFlow()

    fun show(scene: LockScreenScene) {
        _scene.value = scene
    }

    fun clear() {
        _scene.value = null
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, LockScreenWallpaperService::class.java)

        fun isLiveActive(context: Context): Boolean {
            val manager = WallpaperManager.getInstance(context)
            val info = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK) ?: manager.wallpaperInfo
                } else {
                    manager.wallpaperInfo
                }
            }.getOrNull()
            return info?.component == component(context)
        }

        fun isSupported(context: Context): Boolean {
            val manager = WallpaperManager.getInstance(context)
            return manager.isWallpaperSupported && manager.isSetWallpaperAllowed &&
                pickerIntent(context).resolveActivity(context.packageManager) != null
        }

        fun pickerIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
    }
}
