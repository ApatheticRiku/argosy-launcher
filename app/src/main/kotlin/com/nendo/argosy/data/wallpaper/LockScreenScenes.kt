package com.nendo.argosy.data.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SecondaryHomeComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockScreenScene {
    class Hero(val bitmap: Bitmap) : LockScreenScene
    class Mosaic(val rows: List<MosaicRow>, val dimAlpha: Int) : LockScreenScene
}

private const val TAG = "LockScreenScenes"

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

        fun isLiveActive(context: Context): Boolean = liveSlots(context) != 0

        fun liveSlots(context: Context): Int {
            val manager = WallpaperManager.getInstance(context)
            val ours = component(context)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val onHome = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == ours
                    val lockInfo = manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)
                    val onLock = lockInfo?.component == ours || (lockInfo == null && onHome)
                    (if (onHome) WallpaperManager.FLAG_SYSTEM else 0) or (if (onLock) WallpaperManager.FLAG_LOCK else 0)
                } else if (manager.wallpaperInfo?.component == ours) {
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                } else {
                    0
                }
            }.onFailure { Logger.warn(TAG, "liveSlots: could not read the wallpaper info | ${it.message}") }
                .getOrDefault(0)
        }

        fun canOffer(context: Context, lockScreenArt: Boolean): Boolean =
            lockScreenArt && SecondaryHomeComponent.isDefaultHome(context) && isSupported(context)

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
