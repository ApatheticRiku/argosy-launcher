package com.nendo.argosy.data.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import com.nendo.argosy.util.SecondaryHomeComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LockScreenArt"
private const val MOSAIC_LOAD_BATCH = 8

@Singleton
class LockScreenArtManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayPrefs: DisplayPreferencesRepository,
    private val gameRepository: GameRepository,
    private val playSessionTracker: PlaySessionTracker
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val mutex = Mutex()
    private var shownKey: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) scope.launch { refresh() }
        }
    }

    fun start() {
        context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        scope.launch {
            displayPrefs.preferences
                .map { it.lockScreenArt }
                .distinctUntilChanged()
                .collect { enabled -> if (!enabled) mutex.withLock { clearIfApplied() } }
        }
    }

    private suspend fun refresh() = mutex.withLock {
        val enabled = displayPrefs.preferences.first().lockScreenArt
        val isHome = SecondaryHomeComponent.isDefaultHome(context)
        if (!enabled || !isHome) {
            Logger.debug(TAG, "refresh: not drawing | enabled=$enabled, isHome=$isHome")
            clearIfApplied()
            return@withLock
        }
        val wallpaperManager = WallpaperManager.getInstance(context)
        if (!wallpaperManager.isWallpaperSupported || !wallpaperManager.isSetWallpaperAllowed) {
            Logger.debug(TAG, "refresh: wallpaper changes are not allowed on this device")
            return@withLock
        }
        val (width, height) = screenSize() ?: return@withLock

        val gameId = playSessionTracker.activeSession.value?.gameId
        val (key, bitmap) = gameId?.let { heroArt(it, width, height) }
            ?: mosaicArt(width, height)
            ?: run {
                Logger.debug(TAG, "refresh: nothing to draw | gameId=$gameId, size=${width}x$height")
                return@withLock
            }
        if (bitmap == null) return@withLock

        runCatching {
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        }.onSuccess {
            Logger.debug(TAG, "refresh: set the lock wallpaper | key=$key, size=${width}x$height")
            shownKey = key
            displayPrefs.setLockScreenArtApplied(true)
        }.onFailure {
            Logger.warn(TAG, "refresh: could not set the lock wallpaper | key=$key, ${it.message}")
        }
        bitmap.recycle()
    }

    private suspend fun heroArt(gameId: Long, width: Int, height: Int): Pair<String, Bitmap?>? {
        val game = gameRepository.getById(gameId) ?: return null
        for (source in listOfNotNull(game.displayBackgroundPath, game.displayCoverPath)) {
            val key = "game:$gameId:$source:${width}x$height"
            if (key == shownKey) return key to null
            val art = load(source, width, height) ?: continue
            return key to LockScreenArtRenderer.hero(art, width, height)
        }
        Logger.debug(TAG, "heroArt: no art could be loaded | gameId=$gameId")
        return null
    }

    private suspend fun mosaicArt(width: Int, height: Int): Pair<String, Bitmap?>? {
        val grid = LockScreenArtRenderer.grid(width, height)
        val sources = gameRepository.coversOnePerTitle().distinct()
        val key = "mosaic:${sources.size}:${sources.take(grid.tileCount * 2).hashCode()}:${width}x$height"
        if (key == shownKey) return key to null
        val covers = mutableListOf<Bitmap>()
        for (batch in sources.chunked(MOSAIC_LOAD_BATCH)) {
            if (covers.size >= grid.tileCount) break
            covers += coroutineScope {
                batch.map { source -> async { load(source, grid.tileWidth, grid.tileHeight) } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
        val bitmap = LockScreenArtRenderer.mosaic(covers.take(grid.tileCount), width, height) ?: return null
        return key to bitmap
    }

    private suspend fun load(source: String, width: Int, height: Int): Bitmap? {
        val data: Any = if (source.startsWith("/")) File(source).takeIf { it.isFile } ?: return null else source
        val request = ImageRequest.Builder(context)
            .data(data)
            .size(width, height)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        return (result.drawable as? BitmapDrawable)?.bitmap
    }

    private suspend fun clearIfApplied() {
        if (!displayPrefs.isLockScreenArtApplied()) return
        runCatching { WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK) }
            .onFailure { Logger.warn(TAG, "clearIfApplied: could not clear the lock wallpaper | ${it.message}") }
        shownKey = null
        displayPrefs.setLockScreenArtApplied(false)
    }

    private fun screenSize(): Pair<Int, Int>? {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = display.mode
        val sideways = display.rotation == Surface.ROTATION_90 || display.rotation == Surface.ROTATION_270
        return if (sideways) mode.physicalHeight to mode.physicalWidth else mode.physicalWidth to mode.physicalHeight
    }
}
