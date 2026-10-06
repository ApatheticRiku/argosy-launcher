package com.nendo.argosy.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The game screen at the moment a save was written, kept per game until it travels with that save.
 * Captured silently through Argosy's accessibility service, else through the vendor root shell,
 * else not at all.
 */
@Singleton
class SaveScreenshotCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun fileFor(gameId: Long): File = File(dir, "$gameId$EXTENSION")

    /**
     * The screenshot taken for [gameId]'s save, when one was captured within [maxAgeMs].
     */
    fun recentFor(gameId: Long, maxAgeMs: Long = MAX_AGE_MS): File? =
        fileFor(gameId).takeIf {
            it.isFile && it.length() > 0 && System.currentTimeMillis() - it.lastModified() <= maxAgeMs
        }

    suspend fun capture(gameId: Long, displayId: Int): Boolean = withContext(Dispatchers.IO) {
        val bitmap = FocusAccessibilityService.instance?.captureDisplay(displayId) ?: rootCapture(displayId)
        if (bitmap == null) {
            Logger.debug(TAG, "No screenshot for game $gameId: no silent capture route on display $displayId")
            return@withContext false
        }
        store(gameId, bitmap).also { bitmap.recycle() }
    }

    fun store(gameId: Long, frame: Bitmap): Boolean {
        val scaled = scale(frame)
        val written = writeJpeg(fileFor(gameId), scaled)
        if (scaled !== frame) scaled.recycle()
        Logger.debug(TAG, "Save screenshot for game $gameId | written=$written")
        return written
    }

    /**
     * The frame kept locally for [snapshotId] after this device pushed it, so the snapshot view
     * shows it without fetching from the server.
     */
    fun snapshotThumbFor(snapshotId: Long): File? =
        File(snapshotDir, "$snapshotId$EXTENSION").takeIf { it.isFile && it.length() > 0 }

    fun keepForSnapshot(snapshotId: Long, frame: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(frame.absolutePath)
        if (bitmap == null) {
            Logger.warn(TAG, "Snapshot $snapshotId frame unreadable: ${frame.name}")
            return false
        }
        val scaled = scale(bitmap)
        val written = writeJpeg(File(snapshotDir, "$snapshotId$EXTENSION"), scaled)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        pruneSnapshotThumbs()
        return written
    }

    fun carrySnapshotThumb(fromSnapshotId: Long, toSnapshotId: Long) {
        val source = snapshotThumbFor(fromSnapshotId) ?: return
        writeAtomically(File(snapshotDir, "$toSnapshotId$EXTENSION")) { temp -> source.copyTo(temp, overwrite = true) }
        pruneSnapshotThumbs()
    }

    private val snapshotDir: File get() = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }

    private fun writeJpeg(target: File, bitmap: Bitmap): Boolean = writeAtomically(target) { temp ->
        val encoded = temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        check(encoded) { "JPEG encoder refused the frame" }
    }

    private fun writeAtomically(target: File, write: (File) -> Unit): Boolean {
        val temp = File(target.parentFile, "${target.name}$TEMP_SUFFIX")
        return runCatching {
            write(temp)
            check(temp.renameTo(target)) { "rename to ${target.name} failed" }
        }.onFailure {
            temp.delete()
            Logger.warn(TAG, "Writing ${target.name} failed: ${it.message}")
        }.isSuccess
    }

    private fun pruneSnapshotThumbs() {
        val files = snapshotDir.listFiles { file -> file.name.endsWith(EXTENSION) }
            ?.takeIf { it.size > MAX_SNAPSHOT_THUMBS } ?: return
        files.sortedByDescending { it.lastModified() }.drop(MAX_SNAPSHOT_THUMBS).forEach { it.delete() }
    }

    private fun rootCapture(displayId: Int): Bitmap? {
        if (!RootShell.isAvailable) return null
        val shot = File(context.cacheDir, ROOT_SHOT).apply { delete() }
        val result = RootShell.run(
            context,
            "screencap -d $displayId -p ${RootShell.quote(shot.absolutePath)} && chmod 644 ${RootShell.quote(shot.absolutePath)}"
        )
        if (result?.succeeded != true || !shot.canRead()) return null
        return BitmapFactory.decodeFile(shot.absolutePath).also { shot.delete() }
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_WIDTH) return bitmap
        val height = bitmap.height * MAX_WIDTH / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, height, true)
    }

    private companion object {
        const val TAG = "SaveScreenshot"
        const val DIR = "save_screenshots"
        const val SNAPSHOT_DIR = "snapshot_thumbs"
        const val MAX_SNAPSHOT_THUMBS = 500
        const val EXTENSION = ".jpg"
        const val TEMP_SUFFIX = ".tmp"
        const val ROOT_SHOT = "save_screenshot_root.png"
        const val MAX_WIDTH = 640
        const val JPEG_QUALITY = 85
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
