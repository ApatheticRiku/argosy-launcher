package com.nendo.argosy.ui.components.gamelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.CompletionProgress
import com.nendo.argosy.domain.model.RatingTier
import com.nendo.argosy.ui.common.GameListDetails
import com.nendo.argosy.ui.common.genreShortLabelRes
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat

private const val FULL_PERCENT = 100f

internal fun ratingFitsFooter(tabsWidth: Int, ratingWidth: Int, gap: Int, available: Int): Boolean =
    tabsWidth + gap + ratingWidth <= available

@Composable
internal fun GameListRowTabs(
    platformDisplayName: String,
    details: GameListDetails,
    cornerRadius: Dp,
    focusInset: Dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val genreLabel = details.genre?.let { genre -> genreShortLabelRes(genre)?.let { stringResource(it) } ?: genre }
    val labels = listOfNotNull(platformDisplayName, details.releaseYear?.toString(), genreLabel)
    val fills = listOf(
        primary,
        primary.copy(alpha = ComponentDefaults.GameListRow.footerSecondTabAlpha).compositeOver(base),
        primary.copy(alpha = ComponentDefaults.GameListRow.footerThirdTabAlpha).compositeOver(base)
    )
    val inks = listOf(MaterialTheme.colorScheme.onPrimary, onSurface, onSurface)
    val earShape = remember(cornerRadius) { FooterEarShape(cornerRadius) }
    val tabShape = RoundedCornerShape(topEnd = cornerRadius)
    Column {
        Box(
            modifier = Modifier
                .offset(x = focusInset, y = Dimens.borderThin)
                .size(cornerRadius)
                .clip(earShape)
                .background(primary)
        )
        Row(
            modifier = Modifier.height(ComponentDefaults.GameListRow.footerHeight.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            labels.forEachIndexed { index, label ->
                val behind = if (index < labels.lastIndex) fills[index + 1] else Color.Transparent
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(behind)
                        .background(fills[index], tabShape)
                        .padding(horizontal = Dimens.spacingMd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = inks[index],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = -Dimens.borderThin, y = -focusInset)
                    .size(cornerRadius)
                    .clip(earShape)
                    .background(fills[labels.lastIndex])
            )
        }
    }
}

@Composable
internal fun RatingGroup(details: GameListDetails) {
    val rating = details.rating
    if (rating == null && details.userRating <= 0 && details.userDifficulty <= 0) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rating?.let {
            val tierColor = RatingTier.of(it).color(LocalLauncherTheme.current.isDarkTheme)
            RatingStat(
                icon = Icons.Default.Public,
                tint = tierColor,
                value = stringResource(R.string.gamelist_row_rating_value, it.toInt()),
                valueColor = tierColor
            )
        }
        if (details.userRating > 0) {
            RatingStat(
                icon = Icons.Default.Star,
                tint = ColorTokens.Domain.ratingStar,
                value = stringResource(R.string.gamelist_row_user_rating_value, details.userRating),
                valueColor = muted
            )
        }
        if (details.userDifficulty > 0) {
            RatingStat(
                icon = Icons.Default.Whatshot,
                tint = ColorTokens.Domain.difficulty,
                value = stringResource(R.string.gamelist_row_difficulty_value, details.userDifficulty),
                valueColor = muted
            )
        }
    }
}

@Composable
private fun RatingStat(icon: ImageVector, tint: Color, value: String, valueColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconXs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
internal fun ProgressStats(details: GameListDetails) {
    val context = LocalContext.current
    val played = details.playTimeMinutes.takeIf { it > 0 }?.let { formatPlayTime(context, it) }
    val toBeat = formatTimeToBeat(context, details.timeToBeatMainSec)
    val timeValue = when {
        played != null && toBeat != null -> stringResource(R.string.gamelist_row_playtime_of_hltb, played, toBeat)
        else -> played ?: toBeat
    }
    val showAchievements = details.earnedAchievementCount > 0 && details.achievementCount > 0
    if (details.completion == null && timeValue == null && !showAchievements) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        details.completion?.let { CompletionBar(progress = it, isDark = LocalLauncherTheme.current.isDarkTheme) }
        timeValue?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showAchievements) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = ColorTokens.Domain.trophyAmber,
                    modifier = Modifier.size(Dimens.iconXs)
                )
                Text(
                    text = stringResource(
                        R.string.gamelist_row_achievements_value,
                        details.earnedAchievementCount,
                        details.achievementCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CompletionBar(progress: CompletionProgress, isDark: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val track = if (isDark) ColorTokens.Domain.CompletionBar.Track.dark else ColorTokens.Domain.CompletionBar.Track.light
    val fill = when (progress) {
        is CompletionProgress.UserSet -> primary
        is CompletionProgress.Estimate -> primary.copy(
            alpha = if (isDark) {
                ComponentDefaults.GameListRow.estimateFillAlphaDark
            } else {
                ComponentDefaults.GameListRow.estimateFillAlphaLight
            }
        )
    }
    Box(
        modifier = Modifier
            .width(ComponentDefaults.GameListRow.completionBarWidth.dp)
            .height(ComponentDefaults.GameListRow.completionBarHeight.dp)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.percent / FULL_PERCENT)
                .background(fill)
        )
    }
}

private fun RatingTier.color(isDark: Boolean): Color = when (this) {
    RatingTier.LOW -> if (isDark) ColorTokens.Domain.RatingTier.Low.dark else ColorTokens.Domain.RatingTier.Low.light
    RatingTier.MID -> if (isDark) ColorTokens.Domain.RatingTier.Mid.dark else ColorTokens.Domain.RatingTier.Mid.light
    RatingTier.HIGH -> if (isDark) ColorTokens.Domain.RatingTier.High.dark else ColorTokens.Domain.RatingTier.High.light
}

private class FooterEarShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, r)
            lineTo(r, r)
            arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}
