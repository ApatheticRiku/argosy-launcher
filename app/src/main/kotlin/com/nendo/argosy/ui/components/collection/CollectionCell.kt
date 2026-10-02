package com.nendo.argosy.ui.components.collection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.CollectionSummary
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.gamelist.ListRowTabs
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.focusBorder
import com.nendo.argosy.util.formatPlayTime
import kotlin.math.ceil

@Composable
fun CollectionCell(
    name: String,
    gameCountLabel: String,
    summary: CollectionSummary,
    coverPaths: List<String>,
    placeholderIcon: ImageVector,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pinnedDescription: String? = null,
    onLongClick: () -> Unit = {}
) {
    val cornerRadius = LocalBoxArtStyle.current.cornerRadiusDp
    val shape = RoundedCornerShape(cornerRadius)
    val focusWidth = ComponentDefaults.GameListRow.focusBorderWidth.dp
    val footerHeight = ComponentDefaults.GameListRow.footerHeight.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentDefaults.CollectionCell.rowHeight.dp)
            .focusBorder(isFocused, MaterialTheme.colorScheme.primary, focusWidth, shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CoverStrip(
                    coverPaths = coverPaths,
                    placeholderIcon = placeholderIcon,
                    scrolls = isFocused,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(footerHeight))
            }
            pinnedDescription?.let {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = it,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.spacingSm)
                        .size(Dimens.iconSm)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    ListRowTabs(
                        labels = listOf(name),
                        cornerRadius = cornerRadius,
                        focusInset = if (isFocused) focusWidth else 0.dp
                    )
                }
                SummaryStats(
                    gameCountLabel = gameCountLabel,
                    summary = summary,
                    modifier = Modifier
                        .weight(1f)
                        .height(footerHeight)
                )
            }
        }
    }
}

@Composable
private fun CoverStrip(
    coverPaths: List<String>,
    placeholderIcon: ImageVector,
    scrolls: Boolean,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface
    if (coverPaths.isEmpty()) {
        Box(modifier = modifier.background(surface), contentAlignment = Alignment.Center) {
            Icon(
                placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconLg)
            )
        }
        return
    }
    val aspectRatio = LocalBoxArtStyle.current.aspectRatio
    val gap = ComponentDefaults.CollectionCell.mosaicCoverGap.dp
    BoxWithConstraints(modifier = modifier.background(surface)) {
        val coverWidth = maxHeight * aspectRatio
        val slots = ceil(maxWidth / (coverWidth + gap)).toInt().coerceAtLeast(1)
        if (coverPaths.size > slots) {
            LoopingCovers(coverPaths, coverWidth, gap, slots, scrolls)
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                coverPaths.forEach { path -> MosaicCover(path, coverWidth) }
            }
        }
    }
}

@Composable
private fun LoopingCovers(
    coverPaths: List<String>,
    coverWidth: Dp,
    gap: Dp,
    slots: Int,
    scrolls: Boolean
) {
    val density = LocalDensity.current
    val stepPx = with(density) { (coverWidth + gap).toPx() }
    val loopPx = stepPx * coverPaths.size
    val velocityPx = with(density) { ComponentDefaults.CollectionCell.mosaicScrollDpPerSecond.dp.toPx() }
    val offset = remember(coverPaths) { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }
    LaunchedEffect(scrolls, loopPx) {
        if (scrolls) delay(ComponentDefaults.CollectionCell.mosaicScrollStartDelayMs.toLong())
        launch {
            speed.animateTo(
                if (scrolls) 1f else 0f,
                tween(ComponentDefaults.CollectionCell.mosaicScrollRampMs, easing = FastOutSlowInEasing)
            )
        }
        var lastFrame = 0L
        while (scrolls || speed.value > 0f) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame) / NANOS_PER_SECOND
                    offset.floatValue = (offset.floatValue + speed.value * velocityPx * seconds) % loopPx
                }
                lastFrame = now
            }
        }
    }
    val first = (offset.floatValue / stepPx).toInt()
    val within = offset.floatValue - first * stepPx
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset(-within.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            for (k in 0..slots) {
                val position = first + k
                key(position) { MosaicCover(coverPaths[position % coverPaths.size], coverWidth) }
            }
        }
    }
}

@Composable
private fun MosaicCover(path: String, coverWidth: Dp) {
    AsyncImage(
        model = rememberFileImageModel(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(coverWidth)
            .fillMaxHeight()
    )
}

private const val NANOS_PER_SECOND = 1_000_000_000f

@Composable
private fun SummaryStats(
    gameCountLabel: String,
    summary: CollectionSummary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.padding(horizontal = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Stat(Icons.Default.SportsEsports, muted, gameCountLabel)
        if (summary.installedCount > 0) {
            Stat(
                Icons.Default.CheckCircle,
                MaterialTheme.colorScheme.primary,
                stringResource(R.string.collections_cell_installed, summary.installedCount)
            )
        }
        if (summary.totalAchievements > 0) {
            Stat(
                Icons.Default.EmojiEvents,
                ColorTokens.Domain.trophyAmber,
                stringResource(
                    R.string.collections_cell_achievements,
                    summary.earnedAchievements,
                    summary.totalAchievements
                )
            )
        }
        if (summary.playTimeMinutes > 0) {
            Stat(Icons.Default.Schedule, muted, formatPlayTime(context, summary.playTimeMinutes))
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, tint: Color, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconXs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
