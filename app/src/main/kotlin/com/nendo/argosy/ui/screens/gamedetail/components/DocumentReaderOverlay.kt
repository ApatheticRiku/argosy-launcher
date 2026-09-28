package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.Dimens

data class DocumentReaderState(
    val title: String,
    val textPages: List<String> = emptyList(),
    val pages: List<android.graphics.Bitmap> = emptyList(),
    val pageIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorReason: String? = null,
    val showsSpreads: Boolean = false,
    val linesPerPage: Int = TEXT_LINES_PER_PAGE,
    val highlights: List<com.nendo.argosy.data.repository.DocumentHighlight> = emptyList()
) {
    val textPageStart: Int get() = pageIndex * linesPerPage

    val pageCount: Int get() = if (pages.isNotEmpty()) pages.size else textPages.size

    val usesSpreads: Boolean
        get() = showsSpreads && pages.size > 1 &&
            !isFlatSpreadScan(pages.map { it.width to it.height })

    val fraction: Float get() = if (pageCount > 1) pageIndex.toFloat() / (pageCount - 1) else 0f

    val visiblePages: IntRange
        get() = if (usesSpreads) spreadOf(pageIndex, pages.size) else pageIndex..pageIndex
}

/**
 * True when most pages of a scanned document are more than [FLAT_SPREAD_MIN_RATIO] times as wide as
 * they are tall: each page is already an open booklet scanned flat, so pairing them would show two
 * spreads at once.
 */
fun isFlatSpreadScan(pageSizes: List<Pair<Int, Int>>): Boolean {
    if (pageSizes.isEmpty()) return false
    val flat = pageSizes.count { (width, height) -> height > 0 && width > height * FLAT_SPREAD_MIN_RATIO }
    return flat * 2 > pageSizes.size
}

const val FLAT_SPREAD_MIN_RATIO = 2.0f

/**
 * The pages shown together as a book spread when [pageIndex] is open: the cover alone, then each
 * even page beside the odd page after it.
 */
fun spreadOf(pageIndex: Int, pageCount: Int): IntRange {
    if (pageCount <= 0) return 0..0
    val index = pageIndex.coerceIn(0, pageCount - 1)
    if (index == 0) return 0..0
    val start = if (index % 2 == 1) index else index - 1
    return start..minOf(start + 1, pageCount - 1)
}

/**
 * The first page of the spread [delta] spreads away from the one holding [pageIndex], clamped to
 * the book.
 */
fun spreadStartAfter(pageIndex: Int, pageCount: Int, delta: Int): Int {
    if (pageCount <= 0) return 0
    val current = spreadOf(pageIndex, pageCount)
    return when {
        delta > 0 -> (current.last + 1).coerceAtMost(pageCount - 1).let { spreadOf(it, pageCount).first }
        delta < 0 -> spreadOf((current.first - 1).coerceAtLeast(0), pageCount).first
        else -> current.first
    }
}

/**
 * Splits a plain-text document into pages of [linesPerPage] lines each.
 */
fun paginateText(body: String, linesPerPage: Int = TEXT_LINES_PER_PAGE): List<String> =
    body.lines()
        .chunked(linesPerPage)
        .map { it.joinToString("\n") }
        .ifEmpty { listOf("") }

const val TEXT_LINES_PER_PAGE = 34
private const val TEXT_COLUMNS = 80
private const val DEFAULT_LINE_SPACING = 1.4f
private const val MIN_LINES_PER_PAGE = 8
private const val MAX_LINES_PER_PAGE = 120

@Composable
fun DocumentReaderOverlay(
    state: DocumentReaderState,
    onLinesPerPageMeasured: (Int) -> Unit,
    onDismiss: () -> Unit,
    onTurnPage: (Int) -> Unit,
    onSpreadsMeasured: (Boolean) -> Unit = {},
    onToggleHighlight: (Int) -> Unit = {},
    onCycleHighlightColor: (Int) -> Unit = {},
    showsControllerHints: Boolean = true
) {
    val currentOnTurnPage by androidx.compose.runtime.rememberUpdatedState(onTurnPage)
    val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val zoom = remember(state.pageIndex) { PageZoom() }
    val verticalTurns = state.pages.isEmpty()
    val currentVerticalTurns by androidx.compose.runtime.rememberUpdatedState(verticalTurns)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = READER_SCRIM_ALPHA))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val position = if (currentVerticalTurns) offset.y else offset.x
                    val length = if (currentVerticalTurns) size.height else size.width
                    val zone = length / TAP_ZONES
                    when {
                        position < zone -> currentOnTurnPage(-1)
                        position > length - zone -> currentOnTurnPage(1)
                        else -> currentOnDismiss()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingXl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state.pageCount > 1) {
                    val shown = state.visiblePages
                    Text(
                        text = if (shown.first != shown.last) {
                            stringResource(
                                R.string.gamedetail_document_reader_spread,
                                shown.first + 1,
                                shown.last + 1,
                                state.pageCount
                            )
                        } else {
                            stringResource(
                                R.string.gamedetail_document_reader_page,
                                shown.first + 1,
                                state.pageCount
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Box(
                    modifier = Modifier
                        .size(Dimens.iconXl)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickableNoFocus(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = stringResource(R.string.gamedetail_document_reader_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(zoom, verticalTurns) {
                        pageGestures(
                            zoom = zoom,
                            zoomable = !verticalTurns,
                            vertical = verticalTurns,
                            onSwipe = { currentOnTurnPage(it) }
                        )
                    }
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.errorReason != null -> Text(
                        text = stringResource(R.string.gamedetail_document_reader_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.pages.isNotEmpty() -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val wide = maxWidth > maxHeight
                        LaunchedEffect(wide) { onSpreadsMeasured(wide) }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom.scale
                                    scaleY = zoom.scale
                                    translationX = zoom.offset.x
                                    translationY = zoom.offset.y
                                },
                            horizontalArrangement = Arrangement.Center
                        ) {
                            state.visiblePages.forEach { index ->
                                state.pages.getOrNull(index)?.let { page ->
                                    androidx.compose.foundation.Image(
                                        bitmap = page.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        alignment = when {
                                            state.visiblePages.first == state.visiblePages.last -> Alignment.Center
                                            index == state.visiblePages.first -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val baseStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                        val gutter = Dimens.spacingLg
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val charWidthPx = measurer.measure("M".repeat(TEXT_COLUMNS), baseStyle)
                                .size.width.toFloat() / TEXT_COLUMNS
                            val textWidthPx = constraints.maxWidth - with(density) { gutter.toPx() }
                            val scale = if (charWidthPx > 0f) {
                                (textWidthPx / (charWidthPx * TEXT_COLUMNS)).coerceAtMost(1f)
                            } else {
                                1f
                            }
                            val textStyle = baseStyle.copy(
                                fontSize = baseStyle.fontSize * scale,
                                lineHeight = if (baseStyle.lineHeight.isSp) baseStyle.lineHeight * scale else baseStyle.lineHeight
                            )
                            val lineHeightPx = with(density) {
                                textStyle.lineHeight.takeIf { it.isSp }?.toPx()
                                    ?: (textStyle.fontSize.toPx() * DEFAULT_LINE_SPACING)
                            }
                            val fits = ((constraints.maxHeight / lineHeightPx).toInt() - 1)
                                .coerceIn(MIN_LINES_PER_PAGE, MAX_LINES_PER_PAGE)
                            LaunchedEffect(fits) { onLinesPerPageMeasured(fits) }
                            HighlightableText(
                                text = state.textPages.getOrNull(state.pageIndex).orEmpty(),
                                style = textStyle,
                                gutter = gutter,
                                pageStart = state.textPageStart,
                                highlights = state.highlights,
                                onLongPressLine = onToggleHighlight,
                                onBookmarkTap = onCycleHighlightColor
                            )
                        }
                    }
                }
            }
            if (showsControllerHints) {
                FooterHints(
                    hints = buildList {
                        add(
                            InputButton.DPAD_HORIZONTAL to
                                stringResource(R.string.gamedetail_document_reader_turn_page)
                        )
                        if (state.pages.isEmpty() && state.textPages.isNotEmpty()) {
                            add(InputButton.X to stringResource(R.string.gamedetail_document_reader_highlight))
                            if (state.highlights.isNotEmpty()) {
                                add(
                                    InputButton.Y to
                                        stringResource(R.string.gamedetail_document_reader_next_highlight)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HighlightableText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    gutter: androidx.compose.ui.unit.Dp,
    pageStart: Int,
    highlights: List<com.nendo.argosy.data.repository.DocumentHighlight>,
    onLongPressLine: (Int) -> Unit,
    onBookmarkTap: (Int) -> Unit
) {
    val palette = highlightPalette()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val currentOnLongPress by androidx.compose.runtime.rememberUpdatedState(onLongPressLine)
    val currentOnBookmarkTap by androidx.compose.runtime.rememberUpdatedState(onBookmarkTap)
    val currentHighlights by androidx.compose.runtime.rememberUpdatedState(highlights)
    val currentPageStart by androidx.compose.runtime.rememberUpdatedState(pageStart)
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { Dimens.radiusSm.toPx() }
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        softWrap = false,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val result = layout ?: return@drawBehind
                val gutterPx = gutter.toPx()
                val pageLines = result.lineCount
                highlights.forEach { highlight ->
                    val range = highlight.lines
                    val color = palette[highlight.colorIndex.mod(palette.size)]
                    val first = (range.first - pageStart).coerceAtLeast(0)
                    val last = (range.last - pageStart).coerceAtMost(pageLines - 1)
                    if (first > last || first >= pageLines) return@forEach
                    val top = result.getLineTop(first)
                    val bottom = result.getLineBottom(last)
                    val left = gutterPx - cornerPx
                    drawRoundRect(
                        color = color.copy(alpha = HIGHLIGHT_FILL_ALPHA),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(size.width - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx)
                    )
                    drawRoundRect(
                        color = color.copy(alpha = HIGHLIGHT_OUTLINE_ALPHA),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(size.width - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = Dimens.borderThin.toPx())
                    )
                    if (range.first >= pageStart) drawBookmark(color, gutterPx, top)
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    var released = false
                    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        up = waitForUpOrCancellation()
                        released = true
                    }
                    val result = layout ?: return@awaitEachGesture
                    val gutterPx = gutter.toPx()
                    if (released) {
                        val tapUp = up ?: return@awaitEachGesture
                        if (down.position.x > gutterPx) return@awaitEachGesture
                        val line = result.getLineForVerticalPosition(down.position.y) + currentPageStart
                        val marked = currentHighlights.firstOrNull { line in it.lines && it.lines.first >= currentPageStart }
                            ?: return@awaitEachGesture
                        tapUp.consume()
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        currentOnBookmarkTap(marked.lines.first)
                    } else if (finished == null) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        currentOnLongPress(result.getLineForVerticalPosition(down.position.y))
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .padding(start = gutter)
    )
}

@Composable
private fun highlightPalette(): List<androidx.compose.ui.graphics.Color> {
    val accent = com.nendo.argosy.ui.theme.LocalArgosyTheme.current.focusAccent
    val semantic = com.nendo.argosy.ui.theme.generated.ColorTokens.Semantic.Dark
    return listOf(
        accent,
        com.nendo.argosy.ui.theme.generated.ColorTokens.Domain.trophyAmber,
        semantic.success,
        semantic.warning,
        semantic.info
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBookmark(
    color: androidx.compose.ui.graphics.Color,
    gutterPx: Float,
    top: Float
) {
    val width = gutterPx * BOOKMARK_WIDTH_FRACTION
    val height = width * BOOKMARK_ASPECT
    val left = (gutterPx - width) / 2f - gutterPx * BOOKMARK_INSET_FRACTION
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(left, top)
        lineTo(left + width, top)
        lineTo(left + width, top + height)
        lineTo(left + width / 2f, top + height * BOOKMARK_NOTCH)
        lineTo(left, top + height)
        close()
    }
    drawPath(path, color)
}

internal const val HIGHLIGHT_PALETTE_SIZE = 5
private const val HIGHLIGHT_FILL_ALPHA = 0.14f
private const val HIGHLIGHT_OUTLINE_ALPHA = 0.7f
private const val BOOKMARK_WIDTH_FRACTION = 0.5f
private const val BOOKMARK_ASPECT = 1.6f
private const val BOOKMARK_NOTCH = 0.72f
private const val BOOKMARK_INSET_FRACTION = 0.1f
private const val READER_SCRIM_ALPHA = 0.95f
private const val TAP_ZONES = 3
private const val MAX_ZOOM = 4f
private const val SWIPE_FRACTION = 0.12f

@androidx.compose.runtime.Stable
private class PageZoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    val isZoomed: Boolean get() = scale > 1f

    fun apply(zoomBy: Float, pan: Offset, size: IntSize) {
        scale = (scale * zoomBy).coerceIn(1f, MAX_ZOOM)
        val maxX = (scale - 1f) * size.width / 2f
        val maxY = (scale - 1f) * size.height / 2f
        offset = Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY)
        )
    }
}

private suspend fun PointerInputScope.pageGestures(
    zoom: PageZoom,
    zoomable: Boolean,
    vertical: Boolean,
    onSwipe: (Int) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var dragX = 0f
        var dragY = 0f
        var pinched = false
        var moved = false
        do {
            val event = awaitPointerEvent()
            if (zoomable && event.changes.size > 1) pinched = true
            val pan = event.calculatePan()
            if (zoomable && (pinched || zoom.isZoomed)) {
                zoom.apply(event.calculateZoom(), pan, size)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else {
                dragX += pan.x
                dragY += pan.y
                if (moved || kotlin.math.abs(dragX) > viewConfiguration.touchSlop ||
                    kotlin.math.abs(dragY) > viewConfiguration.touchSlop
                ) {
                    moved = true
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        val along = if (vertical) dragY else dragX
        val across = if (vertical) dragX else dragY
        val length = if (vertical) size.height else size.width
        val swiped = !pinched && !zoom.isZoomed &&
            kotlin.math.abs(along) > length * SWIPE_FRACTION &&
            kotlin.math.abs(along) > kotlin.math.abs(across)
        if (swiped) onSwipe(if (along < 0) 1 else -1)
    }
}
