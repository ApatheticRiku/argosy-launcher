package com.nendo.argosy.data.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil

private const val COVER_ASPECT = 2f / 3f
private const val LANDSCAPE_ROWS = 3
private const val PORTRAIT_ROWS = 5
private const val TILE_GAP_FRACTION = 0.02f
private const val MOSAIC_DIM_ALPHA = 90
private const val STRIP_EXTRA_TILES = 3
private const val SECONDS_PER_TILE = 14f
private val ROW_SPEEDS = listOf(1f, 0.8f, 1.15f, 0.9f, 1.05f)

internal data class MosaicGrid(val rows: Int, val columns: Int, val tileWidth: Int, val tileHeight: Int, val gap: Int) {
    val tileCount: Int get() = rows * columns
}

internal object LockScreenArtRenderer {

    fun grid(width: Int, height: Int): MosaicGrid {
        val rows = if (width >= height) LANDSCAPE_ROWS else PORTRAIT_ROWS
        val gap = (height * TILE_GAP_FRACTION).toInt()
        val tileHeight = (height - gap * (rows + 1)) / rows
        val tileWidth = (tileHeight * COVER_ASPECT).toInt()
        val columns = ceil((width + gap).toFloat() / (tileWidth + gap)).toInt() + 1
        return MosaicGrid(rows, columns, tileWidth, tileHeight, gap)
    }

    fun hero(source: Bitmap, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, centerCrop(source, width.toFloat() / height), Rect(0, 0, width, height), paint())
        return output
    }

    fun mosaic(covers: List<Bitmap>, width: Int, height: Int): Bitmap? {
        if (covers.isEmpty()) return null
        val grid = grid(width, height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val gridWidth = grid.columns * grid.tileWidth + (grid.columns - 1) * grid.gap
        val startX = (width - gridWidth) / 2f
        val paint = paint()
        for (row in 0 until grid.rows) {
            for (column in 0 until grid.columns) {
                val cover = covers[(row * grid.columns + column) % covers.size]
                val left = startX + column * (grid.tileWidth + grid.gap)
                val top = grid.gap + row * (grid.tileHeight + grid.gap).toFloat()
                canvas.drawBitmap(
                    cover,
                    centerCrop(cover, COVER_ASPECT),
                    RectF(left, top, left + grid.tileWidth, top + grid.tileHeight),
                    paint
                )
            }
        }
        canvas.drawColor(Color.argb(MOSAIC_DIM_ALPHA, 0, 0, 0))
        return output
    }

    fun stripTileCount(width: Int, height: Int): Int {
        val grid = grid(width, height)
        return grid.rows * (grid.columns + STRIP_EXTRA_TILES)
    }

    fun scrollingMosaic(covers: List<Bitmap>, width: Int, height: Int): LockScreenScene.Mosaic? {
        if (covers.isEmpty()) return null
        val grid = grid(width, height)
        val perRow = grid.columns + STRIP_EXTRA_TILES
        val step = grid.tileWidth + grid.gap
        val paint = paint()
        val rows = (0 until grid.rows).map { row ->
            val strip = Bitmap.createBitmap(perRow * step, grid.tileHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(strip)
            for (column in 0 until perRow) {
                val cover = covers[(row * perRow + column) % covers.size]
                val left = column * step.toFloat()
                canvas.drawBitmap(
                    cover,
                    centerCrop(cover, COVER_ASPECT),
                    RectF(left, 0f, left + grid.tileWidth, grid.tileHeight.toFloat()),
                    paint
                )
            }
            MosaicRow(
                strip = strip,
                top = grid.gap + row * (grid.tileHeight + grid.gap).toFloat(),
                period = (perRow * step).toFloat(),
                pixelsPerSecond = step / SECONDS_PER_TILE * ROW_SPEEDS[row % ROW_SPEEDS.size]
            )
        }
        return LockScreenScene.Mosaic(rows, MOSAIC_DIM_ALPHA)
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun centerCrop(source: Bitmap, targetAspect: Float): Rect {
        val sourceAspect = source.width.toFloat() / source.height
        return if (sourceAspect > targetAspect) {
            val cropWidth = (source.height * targetAspect).toInt()
            val left = (source.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / targetAspect).toInt()
            val top = (source.height - cropHeight) / 2
            Rect(0, top, source.width, top + cropHeight)
        }
    }
}
