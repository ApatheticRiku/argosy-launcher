package com.nendo.argosy.data.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LockScreenArt"
private const val MOSAIC_LOAD_BATCH = 8
private const val LAUNCH_DRAW_BUDGET_MS = 2_000L
private const val LAUNCH_RECOLOR_WAIT_MS = 600L

@Singleton
class LockScreenArtManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayPrefs: DisplayPreferencesRepository,
    private val gameRepository: GameRepository,
    private val playSessionTracker: PlaySessionTracker,
    private val scenes: LockScreenScenes
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val mutex = Mutex()
    private var shownKey: String? = null
    @Volatile private var libraryRefresh: Job? = null

    @Volatile private var shownLive = false

    private class LoadedArt<T>(val art: Pair<String, T?>?)

    companion object {
        internal fun mayDraw(gameId: Long?, shownKey: String?, isLaunch: Boolean): Boolean =
            isLaunch || gameId == null || shownKey?.startsWith("game:$gameId:") == true
    }

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
                .collect { enabled -> if (!enabled) mutex.withLock { clearIfApplied(removeLiveWallpaper = true) } }
        }
    }

    /**
     * Puts the game's art on the lock screen before its game screen opens, and waits for the
     * system to finish recoloring its theme from it. A recolor applied later, while the game
     * screen is alive, makes Android rebuild that screen.
     */
    suspend fun showBeforeLaunch(gameId: Long) {
        libraryRefresh?.cancel()
        val recolored = CompletableDeferred<Unit>()
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                recolored.complete(Unit)
            }

            @Deprecated("Required by ComponentCallbacks")
            override fun onLowMemory() = Unit
        }
        val startedAt = SystemClock.elapsedRealtime()
        var armed = false
        try {
            val drawn = withContext(Dispatchers.IO) {
                refresh(gameId, artLoadBudgetMs = LAUNCH_DRAW_BUDGET_MS) {
                    context.registerComponentCallbacks(callbacks)
                    armed = true
                }
            }
            val settled = drawn && armed && withTimeoutOrNull(LAUNCH_RECOLOR_WAIT_MS) { recolored.await() } != null
            Logger.debug(
                TAG,
                "showBeforeLaunch: gameId=$gameId drawn=$drawn " +
                    "recolored=$settled " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )
        } finally {
            if (armed) context.unregisterComponentCallbacks(callbacks)
        }
    }

    fun showLibraryAfterCancelledLaunch() {
        libraryRefresh = scope.launch { refresh(gameId = null) }
    }

    private suspend fun refresh(
        gameId: Long? = playSessionTracker.activeSession.value?.gameId,
        artLoadBudgetMs: Long? = null,
        beforeWrite: () -> Unit = {}
    ): Boolean = mutex.withLock {
        if (!mayDraw(gameId, shownKey, isLaunch = artLoadBudgetMs != null)) {
            Logger.debug(TAG, "refresh: the launch did not set this game's art; leaving the lock screen during play | gameId=$gameId")
            return@withLock false
        }
        val enabled = displayPrefs.preferences.first().lockScreenArt
        val isHome = SecondaryHomeComponent.isDefaultHome(context)
        if (!enabled || !isHome) {
            Logger.debug(TAG, "refresh: not drawing | enabled=$enabled, isHome=$isHome")
            clearIfApplied(removeLiveWallpaper = true)
            return@withLock false
        }
        val wallpaperManager = WallpaperManager.getInstance(context)
        if (!wallpaperManager.isWallpaperSupported || !wallpaperManager.isSetWallpaperAllowed) {
            Logger.debug(TAG, "refresh: wallpaper changes are not allowed on this device")
            return@withLock false
        }
        val (width, height) = screenSize() ?: return@withLock false

        val live = LockScreenScenes.isLiveActive(context)
        if (live != shownLive) {
            shownKey = null
            shownLive = live
        }
        if (live) return@withLock showLive(gameId, width, height, artLoadBudgetMs)

        val loadArt: suspend () -> Pair<String, Bitmap?>? = {
            gameId?.let { heroArt(it, width, height) } ?: mosaicArt(width, height)
        }
        val art = when (val loaded = withinBudget(artLoadBudgetMs, gameId, loadArt)) {
            null -> return@withLock false
            else -> loaded.art
        }
        val (key, bitmap) = art ?: run {
            Logger.debug(TAG, "refresh: nothing to draw | gameId=$gameId, size=${width}x$height")
            return@withLock false
        }
        if (bitmap == null) return@withLock false

        beforeWrite()
        val set = runCatching {
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        }.onSuccess {
            Logger.debug(TAG, "refresh: set the lock wallpaper | key=$key, size=${width}x$height")
            shownKey = key
            displayPrefs.setLockScreenArtApplied(true)
        }.onFailure {
            Logger.warn(TAG, "refresh: could not set the lock wallpaper | key=$key, ${it.message}")
        }.isSuccess
        bitmap.recycle()
        set
    }

    private suspend fun <T> withinBudget(
        budgetMs: Long?,
        gameId: Long?,
        load: suspend () -> Pair<String, T?>?
    ): LoadedArt<T>? {
        if (budgetMs == null) return LoadedArt(load())
        return withTimeoutOrNull(budgetMs) { LoadedArt(load()) } ?: run {
            Logger.debug(TAG, "refresh: art not ready within ${budgetMs}ms; leaving the lock screen as it is | gameId=$gameId")
            null
        }
    }

    private suspend fun showLive(gameId: Long?, width: Int, height: Int, budgetMs: Long?): Boolean {
        releaseStaticLockForLive()
        val loadScene: suspend () -> Pair<String, LockScreenScene?>? = {
            gameId?.let { id -> heroArt(id, width, height)?.let { (key, bitmap) -> key to bitmap?.let(LockScreenScene::Hero) } }
                ?: scrollingMosaic(width, height)
        }
        val (key, scene) = withinBudget(budgetMs, gameId, loadScene)?.art ?: return false
        if (scene == null) return false
        scenes.show(scene)
        shownKey = key
        Logger.debug(TAG, "showLive: sent the live wallpaper a new scene | key=$key")
        return true
    }

    private suspend fun scrollingMosaic(width: Int, height: Int): Pair<String, LockScreenScene?>? {
        val tileCount = LockScreenArtRenderer.stripTileCount(width, height)
        val sources = gameRepository.coversOnePerTitle().distinct()
        val key = "mosaic:${sources.size}:${sources.take(tileCount * 2).hashCode()}:${width}x$height"
        if (key == shownKey) return key to null
        return scrollingScene(sources, width, height)?.let { key to it }
    }

    private suspend fun releaseStaticLockForLive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val wallpaperManager = WallpaperManager.getInstance(context)
        if (wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0) return
        runCatching { wallpaperManager.clear(WallpaperManager.FLAG_LOCK) }
            .onSuccess {
                Logger.debug(TAG, "releaseStaticLockForLive: cleared the still lock image so the live wallpaper shows")
                displayPrefs.setLockScreenArtApplied(false)
            }
            .onFailure { Logger.warn(TAG, "releaseStaticLockForLive: could not clear the lock wallpaper | ${it.message}") }
    }

    fun onLiveWallpaperBound() {
        libraryRefresh = scope.launch { refresh() }
    }

    fun prepareLivePreview() {
        if (scenes.scene.value != null) return
        scope.launch {
            val (width, height) = screenSize() ?: return@launch
            scrollingScene(gameRepository.coversOnePerTitle().distinct(), width, height)?.let(scenes::show)
        }
    }

    private suspend fun scrollingScene(sources: List<String>, width: Int, height: Int): LockScreenScene.Mosaic? {
        val grid = LockScreenArtRenderer.grid(width, height)
        val count = LockScreenArtRenderer.stripTileCount(width, height)
        return LockScreenArtRenderer.scrollingMosaic(loadCovers(sources, count, grid.tileWidth, grid.tileHeight), width, height)
    }

    private suspend fun heroArt(gameId: Long, width: Int, height: Int): Pair<String, Bitmap?>? {
        gameRepository.getById(gameId) ?: return null
        shownKey?.takeIf { it.startsWith("game:$gameId:") }?.let { return it to null }
        val gameArt = gameRepository.getArt(gameId)
        for (source in listOfNotNull(gameArt.backgroundPath, gameArt.coverPath)) {
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
        val covers = loadCovers(sources, grid.tileCount, grid.tileWidth, grid.tileHeight)
        val bitmap = LockScreenArtRenderer.mosaic(covers, width, height) ?: return null
        return key to bitmap
    }

    private suspend fun loadCovers(sources: List<String>, count: Int, width: Int, height: Int): List<Bitmap> {
        val covers = mutableListOf<Bitmap>()
        for (batch in sources.chunked(MOSAIC_LOAD_BATCH)) {
            if (covers.size >= count) break
            covers += coroutineScope {
                batch.map { source -> async { load(source, width, height) } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
        return covers.take(count)
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

    private suspend fun clearIfApplied(removeLiveWallpaper: Boolean = false) {
        if (LockScreenScenes.isLiveActive(context)) {
            scenes.clear()
            shownKey = null
            if (removeLiveWallpaper) {
                runCatching {
                    WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                }.onFailure { Logger.warn(TAG, "clearIfApplied: could not remove the live wallpaper | ${it.message}") }
            }
        }
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
