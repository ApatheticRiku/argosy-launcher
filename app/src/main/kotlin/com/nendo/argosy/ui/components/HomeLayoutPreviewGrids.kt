package com.nendo.argosy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.domain.model.AutoGridConfig
import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.GridUtils
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val AUTO_GRID_SECTIONS = 3
private const val AUTO_GRID_ROWS_PER_SECTION = 2
private const val AUTO_GRID_HORIZONTAL_OVERSCAN = 2
private const val CUSTOM_GRID_PAGES = 3
private const val SCROLL_SCHEMATIC_OVERSCAN = 2

/**
 * Auto-grid schematic. [AutoGridConfig.laneCount] owns the cross-axis count, so it reads as columns
 * when scrolling vertically and as rows when scrolling horizontally; grid density supplies only the
 * cell gap, matching what the library grid puts between covers.
 */
@Composable
internal fun AutoGridSchematic(
    config: AutoGridConfig,
    preview: HomeLayoutPreviewMetrics,
    gridDensity: GridDensity,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val gap = GridUtils.getGridSpacingDp(gridDensity).dp * preview.scale
    val progress = if (animate) {
        rememberLoopProgress(ComponentDefaults.HomeLayoutPreview.scrollCycleMs)
    } else {
        0f
    }
    Canvas(modifier = modifier.clipToBounds().fillMaxSize()) {
        val geometry = AutoGridGeometry(
            lanes = config.laneCount,
            gapPx = gap.toPx(),
            coverAspectRatio = preview.coverAspectRatio,
            cornerPx = preview.coverCornerRadius.toPx(),
            titlePx = if (config.showTitles) preview.barHeight.toPx() else 0f,
            blockColor = preview.block,
            textColor = preview.text
        )
        when (config.scrollAxis) {
            HomeScrollAxis.VERTICAL -> drawVerticalAutoGrid(geometry, progress)
            HomeScrollAxis.HORIZONTAL -> drawHorizontalAutoGrid(geometry, progress)
        }
    }
}

@Composable
internal fun CustomGridSchematic(
    config: CustomGridConfig,
    preview: HomeLayoutPreviewMetrics,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollAxis = config.scrollAxis
    if (scrollAxis != null) {
        ScrollingCustomGridSchematic(config, scrollAxis, preview, animate, modifier)
        return
    }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(ComponentDefaults.HomeLayoutPreview.pageDwellMs.toLong())
            page += 1
        }
    }
    val offset by animateFloatAsState(
        targetValue = page.toFloat(),
        animationSpec = tween(durationMillis = Motion.durationPage, easing = Motion.argosyEase),
        label = "home-layout-preview-page"
    )
    val dotSize = ComponentDefaults.Carousel.dotSizeActive.dp * preview.scale
    val dotGap = ComponentDefaults.Carousel.dotGap.dp * preview.scale
    Canvas(modifier = modifier.clipToBounds().fillMaxSize()) {
        val gapPx = preview.gap.toPx()
        val dotStrip = dotSize.toPx() + gapPx
        drawCustomGrid(
            metrics = customGridMetrics(
                width = size.width,
                height = size.height - dotStrip,
                columns = config.columns,
                rows = config.rows,
                gapPx = gapPx
            ),
            dotStripPx = dotStrip,
            coverAspectRatio = preview.coverAspectRatio,
            cornerPx = preview.coverCornerRadius.toPx(),
            dotSizePx = dotSize.toPx(),
            dotGapPx = dotGap.toPx(),
            offset = offset,
            blockColor = preview.block,
            activeColor = preview.focus
        )
    }
}

@Composable
private fun ScrollingCustomGridSchematic(
    config: CustomGridConfig,
    scrollAxis: HomeScrollAxis,
    preview: HomeLayoutPreviewMetrics,
    animate: Boolean,
    modifier: Modifier
) {
    val progress = if (animate) {
        rememberLoopProgress(ComponentDefaults.HomeLayoutPreview.scrollCycleMs)
    } else {
        0f
    }
    Canvas(modifier = modifier.clipToBounds().fillMaxSize()) {
        val metrics = customGridMetrics(
            width = size.width,
            height = size.height,
            columns = config.columns,
            rows = config.rows,
            gapPx = preview.gap.toPx()
        )
        val cellWidth = metrics.cellWidthPx
        val cellHeight = metrics.cellHeightPx
        if (cellWidth <= 0f || cellHeight <= 0f) return@Canvas
        val coverWidth = minOf(cellWidth, cellHeight * preview.coverAspectRatio)
        val coverHeight = coverWidth / preview.coverAspectRatio
        val vertical = scrollAxis == HomeScrollAxis.VERTICAL
        val stride = (if (vertical) cellHeight else cellWidth) + metrics.gapPx
        val lanes = if (vertical) metrics.columns else metrics.rows
        val lines = (if (vertical) metrics.rows else metrics.columns) + SCROLL_SCHEMATIC_OVERSCAN
        val shift = progress * stride
        val cornerPx = preview.coverCornerRadius.toPx()
        repeat(lines) { line ->
            val along = line * stride - shift
            repeat(lanes) { lane ->
                val cellX = if (vertical) metrics.offsetXPx + lane * (cellWidth + metrics.gapPx) else along
                val cellY = if (vertical) along else metrics.offsetYPx + lane * (cellHeight + metrics.gapPx)
                drawBlock(
                    x = cellX + (cellWidth - coverWidth) / 2f,
                    y = cellY + (cellHeight - coverHeight) / 2f,
                    width = coverWidth,
                    height = coverHeight,
                    cornerPx = cornerPx,
                    color = preview.block
                )
            }
        }
    }
}

@Composable
private fun rememberLoopProgress(cycleMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "home-layout-preview-loop")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home-layout-preview-progress"
    )
    return progress
}

private class AutoGridGeometry(
    val lanes: Int,
    val gapPx: Float,
    val coverAspectRatio: Float,
    val cornerPx: Float,
    val titlePx: Float,
    val blockColor: Color,
    val textColor: Color
)

private fun DrawScope.drawVerticalAutoGrid(geometry: AutoGridGeometry, progress: Float) {
    val cellWidth = cellWidthOf(geometry)
    if (cellWidth <= 0f) return
    val cellHeight = cellWidth / geometry.coverAspectRatio
    val titleBlock = drawFocusedTitle(geometry)
    val rowHeight = cellHeight + geometry.gapPx
    val sectionHeight = AUTO_GRID_ROWS_PER_SECTION * rowHeight
    val contentHeight = AUTO_GRID_SECTIONS * sectionHeight
    if (contentHeight <= 0f) return
    val base = titleBlock - progress * contentHeight
    repeat(2) { tile ->
        var y = base + tile * contentHeight
        repeat(AUTO_GRID_SECTIONS) {
            repeat(AUTO_GRID_ROWS_PER_SECTION) {
                drawCoverRow(geometry, y, cellWidth, cellHeight, 0f, geometry.lanes)
                y += rowHeight
            }
        }
    }
}

private fun DrawScope.drawHorizontalAutoGrid(geometry: AutoGridGeometry, progress: Float) {
    val titleBlock = drawFocusedTitle(geometry)
    val rowHeight = (size.height - titleBlock) / geometry.lanes
    val cellHeight = rowHeight - geometry.gapPx
    if (cellHeight <= 0f) return
    val cellWidth = cellHeight * geometry.coverAspectRatio
    val itemsPerRow = ceil(size.width / (cellWidth + geometry.gapPx)).toInt()
        .coerceAtLeast(1) + AUTO_GRID_HORIZONTAL_OVERSCAN
    val contentWidth = itemsPerRow * (cellWidth + geometry.gapPx)
    var y = titleBlock
    repeat(geometry.lanes) {
        repeat(2) { tile ->
            val originX = -progress * contentWidth + tile * contentWidth
            drawCoverRow(geometry, y, cellWidth, cellHeight, originX, itemsPerRow)
        }
        y += cellHeight + geometry.gapPx
    }
}

private fun DrawScope.drawCoverRow(
    geometry: AutoGridGeometry,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    originX: Float,
    count: Int
) {
    repeat(count) { column ->
        val x = originX + column * (cellWidth + geometry.gapPx)
        drawBlock(x, y, cellWidth, cellHeight, geometry.cornerPx, geometry.blockColor)
    }
}

/**
 * The one title the auto grid shows: the focused game's name, centred above the grid and fixed
 * while the grid scrolls under it. Covers carry no label of their own, so the schematic must not
 * draw one per cover.
 */
private fun DrawScope.drawFocusedTitle(geometry: AutoGridGeometry): Float {
    if (geometry.titlePx <= 0f) return 0f
    val width = size.width * ComponentDefaults.HomeLayoutPreview.focusedTitleBarWidthRatio
    drawBar(
        x = (size.width - width) / 2f,
        y = 0f,
        width = width,
        height = geometry.titlePx,
        color = geometry.textColor
    )
    return geometry.titlePx + geometry.gapPx
}

private fun DrawScope.cellWidthOf(geometry: AutoGridGeometry): Float =
    (size.width - geometry.gapPx * (geometry.lanes - 1)) / geometry.lanes



private fun DrawScope.drawCustomGrid(
    metrics: CustomGridMetrics,
    dotStripPx: Float,
    coverAspectRatio: Float,
    cornerPx: Float,
    dotSizePx: Float,
    dotGapPx: Float,
    offset: Float,
    blockColor: Color,
    activeColor: Color
) {
    val gapPx = metrics.gapPx
    val cellWidth = metrics.cellWidthPx
    val cellHeight = metrics.cellHeightPx
    if (cellWidth <= 0f || cellHeight <= 0f || size.height <= dotStripPx) return
    val coverWidth = minOf(cellWidth, cellHeight * coverAspectRatio)
    val coverHeight = coverWidth / coverAspectRatio
    val stride = size.width + gapPx
    val within = offset.mod(1f) * stride
    repeat(2) { page ->
        val originX = page * stride - within + metrics.offsetXPx
        repeat(metrics.rows) { row ->
            repeat(metrics.columns) { column ->
                drawBlock(
                    x = originX + column * (cellWidth + gapPx) + (cellWidth - coverWidth) / 2f,
                    y = metrics.offsetYPx + row * (cellHeight + gapPx) + (cellHeight - coverHeight) / 2f,
                    width = coverWidth,
                    height = coverHeight,
                    cornerPx = cornerPx,
                    color = blockColor
                )
            }
        }
    }
    val activePage = offset.roundToInt().mod(CUSTOM_GRID_PAGES)
    val stripWidth = CUSTOM_GRID_PAGES * dotSizePx + (CUSTOM_GRID_PAGES - 1) * dotGapPx
    val stripLeft = (size.width - stripWidth) / 2f
    repeat(CUSTOM_GRID_PAGES) { index ->
        val color = if (index == activePage) {
            activeColor
        } else {
            blockColor.copy(alpha = ComponentDefaults.Carousel.dotInactiveAlpha)
        }
        drawCircle(
            color = color,
            radius = dotSizePx / 2f,
            center = Offset(
                x = stripLeft + index * (dotSizePx + dotGapPx) + dotSizePx / 2f,
                y = size.height - dotSizePx / 2f
            )
        )
    }
}

private fun DrawScope.drawBlock(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    cornerPx: Float,
    color: Color
) {
    if (width <= 0f || height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerPx, cornerPx)
    )
}

private fun DrawScope.drawBar(x: Float, y: Float, width: Float, height: Float, color: Color) {
    drawBlock(x, y, width, height, height / 2f, color)
}
