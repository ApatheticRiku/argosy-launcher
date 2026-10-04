package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.common.playerCountGlyph
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.Box3dCover
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.friends.FriendsActivityBadge
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat

private const val PERCENT = 100f
private const val DEFAULT_STRENGTH = 90f
private const val CINEMATIC_TOP_ALPHA = 0.35f
private const val CINEMATIC_BOTTOM_ALPHA = 0.96f
private const val CINEMATIC_CLEAR_STOP = 0.3f
private const val CINEMATIC_SHADE_RAMP = 0.2f
private const val CINEMATIC_SHADE_ALPHA = 0.8f
private const val JOURNAL_LEFT_ALPHA = 0.96f
private const val JOURNAL_MID_ALPHA = 0.86f
private const val JOURNAL_RIGHT_ALPHA = 0.15f
private const val JOURNAL_MID_STOP = 0.42f
private const val JOURNAL_CLEAR_STOP = 0.8f
private const val LOGO_TOP_ALPHA = 0.1f
private const val LOGO_BOTTOM_ALPHA = 0.7f
private const val CINEMATIC_COVER_HEIGHT_SHARE = 0.36f
private const val JOURNAL_COLUMN_WIDTH_SHARE = 0.56f
private const val SERIES_SCALE = 0.45f
private const val PILL_ALPHA = 0.6f
private const val DIVIDER_ALPHA = 0.25f
private const val TRACK_ALPHA = 0.14f
private const val JOURNAL_CARD_ALPHA = 0.72f

/**
 * A focused game on the presentation screen, drawn in [style]'s layout.
 *
 * @param bottomInset the height of whatever the host draws over the bottom edge, such as relayed
 *   control hints; content stays above it.
 */
@Composable
fun GameShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    bottomInset: Dp,
    modifier: Modifier = Modifier
) {
    val art = detail.gameId?.let { com.nendo.argosy.ui.common.rememberResolvedArt(it) }
    val liveDetail = remember(detail, art) { detail.withLiveArt(art) }
    GameShowcaseContent(liveDetail, style, bottomInset, modifier)
}

private fun CompanionDetail.withLiveArt(art: com.nendo.argosy.data.model.ResolvedGameArt?): CompanionDetail {
    if (art == null) return this
    return copy(
        artUrl = art.coverPath ?: artUrl,
        backdropUrl = art.backgroundPath ?: backdropUrl,
        logoUrl = art.logoPath?.takeIf { it.startsWith("/") } ?: logoUrl
    )
}

@Composable
private fun GameShowcaseContent(
    detail: CompanionDetail,
    style: PresentationStyle,
    bottomInset: Dp,
    modifier: Modifier
) {
    val contentBottom = bottomInset + Dimens.spacingMd
    val theme = LocalArgosyTheme.current
    val friends = detail.stats?.friends.orEmpty().takeIf { style.shows(PresentationStat.FRIENDS) }.orEmpty()
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(theme.surfaceBase)) {
        val gutter = maxWidth * GUTTER_SHARE
        val coverHeight = maxHeight * CINEMATIC_COVER_HEIGHT_SHARE
        val shadeStart = ((maxHeight - contentBottom - coverHeight) / maxHeight)
            .coerceIn(CINEMATIC_CLEAR_STOP, 1f)
        (detail.backdropUrl ?: detail.artUrl)?.let { backdrop ->
            AsyncImage(
                model = rememberFileImageModel(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (style.scrim == PresentationScrim.BLUR) Modifier.blur(Motion.blurRadiusDrawer) else Modifier)
            )
        }
        ShowcaseScrim(style = style, color = theme.surfaceBase, shadeStart = shadeStart)

        when (style.layout) {
            PresentationLayout.LOGO -> LogoShowcase(detail = detail, gutter = gutter)
            PresentationLayout.CINEMATIC -> CinematicShowcase(
                detail = detail,
                style = style,
                friends = friends,
                gutter = gutter,
                bottom = contentBottom,
                coverHeight = coverHeight
            )
            PresentationLayout.JOURNAL -> JournalShowcase(
                detail = detail,
                style = style,
                friends = friends,
                gutter = gutter,
                bottom = contentBottom,
                columnWidth = maxWidth * JOURNAL_COLUMN_WIDTH_SHARE
            )
        }
    }
}

private const val GUTTER_SHARE = 0.0375f

@Composable
private fun ShowcaseScrim(style: PresentationStyle, color: Color, shadeStart: Float) {
    val strength = style.scrimStrength / PERCENT
    val scale = style.scrimStrength / DEFAULT_STRENGTH
    fun tone(alpha: Float) = color.copy(alpha = (alpha * scale).coerceIn(0f, 1f))
    val brush = when (style.scrim) {
        PresentationScrim.NONE -> return
        PresentationScrim.SOLID, PresentationScrim.BLUR -> SolidColor(color.copy(alpha = strength))
        PresentationScrim.GRADIENT -> when (style.layout) {
            PresentationLayout.CINEMATIC -> Brush.verticalGradient(
                0f to tone(CINEMATIC_TOP_ALPHA),
                CINEMATIC_CLEAR_STOP to tone(0f),
                shadeStart to tone(0f),
                (shadeStart + CINEMATIC_SHADE_RAMP).coerceAtMost(1f) to tone(CINEMATIC_SHADE_ALPHA),
                1f to tone(CINEMATIC_BOTTOM_ALPHA)
            )
            PresentationLayout.JOURNAL -> Brush.horizontalGradient(
                0f to tone(JOURNAL_LEFT_ALPHA),
                JOURNAL_MID_STOP to tone(JOURNAL_MID_ALPHA),
                JOURNAL_CLEAR_STOP to tone(JOURNAL_RIGHT_ALPHA),
                1f to tone(JOURNAL_RIGHT_ALPHA)
            )
            PresentationLayout.LOGO -> Brush.verticalGradient(
                0f to tone(LOGO_TOP_ALPHA),
                1f to tone(LOGO_BOTTOM_ALPHA)
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(brush))
}

@Composable
private fun LogoShowcase(detail: CompanionDetail, gutter: Dp) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = gutter, vertical = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        var logoFailed by remember(detail.logoUrl) { mutableStateOf(false) }
        val logo = detail.logoUrl?.takeIf { !logoFailed }
        if (logo != null) {
            val glow = theme.surfaceBase
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val brush = Brush.radialGradient(
                            0f to glow.copy(alpha = LOGO_GLOW_ALPHA),
                            LOGO_GLOW_HOLD to glow.copy(alpha = LOGO_GLOW_ALPHA * LOGO_GLOW_HOLD_FADE),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.height / 2f
                        )
                        scale(scaleX = size.width / size.height, scaleY = 1f, pivot = center) {
                            drawCircle(brush = brush, radius = size.height / 2f, center = center)
                        }
                    }
            )
            AsyncImage(
                model = rememberFileImageModel(logo),
                contentDescription = detail.title,
                contentScale = ContentScale.Fit,
                onError = { logoFailed = true },
                modifier = Modifier
                    .fillMaxWidth(LOGO_WIDTH_SHARE)
                    .fillMaxHeight(LOGO_HEIGHT_SHARE)
            )
        } else {
            GameTitle(
                title = detail.title,
                titleStyle = MaterialTheme.typography.displayLarge,
                titleColor = theme.textPrimary,
                seriesScale = SERIES_SCALE,
                maxLines = 2,
                textAlign = TextAlign.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }
}

private const val LOGO_WIDTH_SHARE = 0.6f
private const val LOGO_HEIGHT_SHARE = 0.5f
private const val LOGO_GLOW_ALPHA = 0.75f
private const val LOGO_GLOW_HOLD = 0.5f
private const val LOGO_GLOW_HOLD_FADE = 0.7f

@Composable
private fun CinematicShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    friends: List<FriendActivity>,
    gutter: Dp,
    bottom: Dp,
    coverHeight: Dp
) {
    val journey = rememberJourney(detail.stats, style)
    Box(modifier = Modifier.fillMaxSize()) {
        if (friends.isNotEmpty()) {
            ShowcaseFriendsPill(
                friends = friends,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = gutter, top = Dimens.spacingLg)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = gutter, end = gutter, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl),
            verticalAlignment = Alignment.Bottom
        ) {
            ShowcaseCover(detail = detail, art = style.art, height = coverHeight)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                ShowcaseTitle(detail = detail)
                ShowcaseRail(rows = showcaseRailRows(detail, style, journey != null))
                journey?.let { ShowcaseJourneyBar(journey = it) }
            }
        }
    }
}

@Composable
private fun JournalShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    friends: List<FriendActivity>,
    gutter: Dp,
    bottom: Dp,
    columnWidth: Dp
) {
    val theme = LocalArgosyTheme.current
    val stats = detail.stats
    val items = stats?.let { showcaseRailItems(it, style, journeyShown = true) }.orEmpty()
    val facts = items.filter { it.first.railGroup == RailGroup.FACTS && it.first != PresentationStat.DEVELOPER }
        .map { it.second }
    val ratings = items.filter { it.first.railGroup == RailGroup.RATINGS }.map { it.second }
    val developer = items.firstOrNull { it.first == PresentationStat.DEVELOPER }?.second?.text
    val factRow = if (stats == null) detail.facts.map { RailItem(null, null, it.value) } else facts
    Column(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight()
            .padding(start = gutter, top = Dimens.spacingXl, bottom = bottom + Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        detail.subtitle?.let { ShowcaseSubtitle(it, detail.platformSlug) }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            GameTitle(
                title = detail.title,
                titleStyle = MaterialTheme.typography.displayMedium,
                titleColor = theme.textPrimary,
                seriesScale = SERIES_SCALE,
                maxLines = 2
            )
            developer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (factRow.isNotEmpty()) {
            ShowcaseRailRow(factRow, textStyle = MaterialTheme.typography.titleSmall, textColor = theme.textDim)
        }
        Box(modifier = Modifier.weight(1f))
        if (ratings.isNotEmpty()) ShowcaseRailRow(ratings)
        stats?.let { JournalJourneyCard(stats = it, style = style) }
        if (friends.isNotEmpty()) {
            FriendsActivityBadge(friends = friends, textColor = theme.textPrimary)
        }
    }
}

@Composable
private fun JournalJourneyCard(stats: CompanionGameStats, style: PresentationStyle) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val played = stats.playTimeMinutes.takeIf { it > 0 && style.shows(PresentationStat.PLAY_TIME) }
        ?.let { formatPlayTime(context, it) }
    val mainStory = stats.timeToBeatMainSec?.takeIf { it > 0 && style.shows(PresentationStat.TIME_TO_BEAT) }
    val mainStoryLabel = mainStory?.let { formatTimeToBeat(context, it) }
    val fraction = if (mainStory != null) {
        (stats.playTimeMinutes * SECONDS_PER_MINUTE / mainStory.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val achievements = stats.takeIf { style.shows(PresentationStat.ACHIEVEMENTS) && it.achievementCount > 0 }
    if (played == null && mainStoryLabel == null && achievements == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceBase.copy(alpha = JOURNAL_CARD_ALPHA))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dual_showcase_journey_header).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                modifier = Modifier.weight(1f)
            )
            fraction?.let {
                Text(
                    text = "${(it * PERCENT).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.focusAccent
                )
            }
        }
        fraction?.let { ShowcaseTrack(fraction = it, color = theme.focusAccent) }
        if (played != null || mainStoryLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = played?.let { stringResource(R.string.dual_showcase_played, it) }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                mainStoryLabel?.let {
                    Text(
                        text = stringResource(R.string.dual_showcase_journey_main, it),
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textDim
                    )
                }
            }
        }
        achievements?.let {
            ShowcaseProgressBar(
                fraction = it.earnedAchievementCount.toFloat() / it.achievementCount,
                color = ALauncherColors.TrophyAmber,
                label = "${it.earnedAchievementCount}/${it.achievementCount}",
                icon = Icons.Filled.EmojiEvents
            )
        }
    }
}

@Composable
private fun ShowcaseTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.spacingXs)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.textPrimary.copy(alpha = TRACK_ALPHA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(color)
        )
    }
}

@Composable
private fun ShowcaseTitle(detail: CompanionDetail) {
    val theme = LocalArgosyTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        detail.subtitle?.let { ShowcaseSubtitle(it, detail.platformSlug) }
        GameTitle(
            title = detail.title,
            titleStyle = MaterialTheme.typography.displayMedium,
            titleColor = theme.textPrimary,
            seriesScale = SERIES_SCALE,
            maxLines = 2
        )
    }
}

@Composable
private fun ShowcaseSubtitle(subtitle: String, platformSlug: String?) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val iconUri = platformSlug?.let { slug -> remember(slug) { PlatformIconAssets.resolveAssetUri(context, slug) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.surfaceBase.copy(alpha = PILL_ALPHA))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        iconUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShowcaseCover(detail: CompanionDetail, art: PresentationArt, height: Dp) {
    val artUrl = detail.artUrl ?: return
    if (art == PresentationArt.TITLE) return
    val localSpine = detail.spineUrl
        ?.takeIf { art == PresentationArt.BOX_3D && artUrl.startsWith("/") && it.startsWith("/") }
    if (localSpine != null) {
        Box3dCover(
            frontPath = artUrl,
            spinePath = localSpine,
            isInteractive = false,
            modifier = Modifier.height(height)
        )
        return
    }
    AsyncImage(
        model = rememberFileImageModel(artUrl),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusSm))
    )
}

@Composable
private fun ShowcaseFriendsPill(friends: List<FriendActivity>, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.surfaceBase.copy(alpha = PILL_ALPHA))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        FriendsActivityBadge(friends = friends, textColor = theme.textPrimary)
    }
}

private data class RailItem(val icon: ImageVector?, val tint: Color?, val text: String)

private enum class RailGroup { FACTS, RATINGS, PROGRESS }

private val PresentationStat.railGroup: RailGroup
    get() = when (this) {
        PresentationStat.DEVELOPER, PresentationStat.RELEASE_YEAR, PresentationStat.PLAYERS,
        PresentationStat.GENRE, PresentationStat.FRIENDS -> RailGroup.FACTS
        PresentationStat.COMMUNITY_RATING, PresentationStat.USER_RATING,
        PresentationStat.DIFFICULTY -> RailGroup.RATINGS
        PresentationStat.PLAY_TIME, PresentationStat.TIME_TO_BEAT,
        PresentationStat.ACHIEVEMENTS -> RailGroup.PROGRESS
    }

@Composable
private fun showcaseRailRows(
    detail: CompanionDetail,
    style: PresentationStyle,
    journeyShown: Boolean
): List<List<RailItem>> {
    val stats = detail.stats ?: return listOf(detail.facts.map { RailItem(null, null, it.value) })
    val items = showcaseRailItems(stats, style, journeyShown)
    return RailGroup.entries.mapNotNull { group ->
        items.filter { it.first.railGroup == group }.map { it.second }.takeIf { it.isNotEmpty() }
    }
}

@Composable
private fun showcaseRailItems(
    stats: CompanionGameStats,
    style: PresentationStyle,
    journeyShown: Boolean
): List<Pair<PresentationStat, RailItem>> {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val playedTemplate = stringResource(R.string.dual_showcase_played)
    return PresentationStat.entries.filter { style.shows(it) }.mapNotNull { stat ->
        when (stat) {
            PresentationStat.DEVELOPER -> stats.developer?.let { RailItem(null, null, it) }
            PresentationStat.RELEASE_YEAR ->
                stats.releaseYear?.let { RailItem(Icons.Default.CalendarToday, null, it.toString()) }
            PresentationStat.PLAYERS ->
                stats.players?.takeIf { it.isNotBlank() }?.let { RailItem(playerCountGlyph(it), null, it) }
            PresentationStat.GENRE -> stats.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { RailItem(Icons.Default.Sell, null, it) }
            PresentationStat.COMMUNITY_RATING ->
                stats.communityRating?.let { RailItem(Icons.Default.Public, primary, "${it.toInt()}%") }
            PresentationStat.USER_RATING -> stats.userRating.takeIf { it > 0 }
                ?.let { RailItem(Icons.Default.Star, ALauncherColors.StarGold, "$it/10") }
            PresentationStat.DIFFICULTY -> stats.userDifficulty.takeIf { it > 0 }
                ?.let { RailItem(Icons.Default.Whatshot, ALauncherColors.DifficultyRed, "$it/10") }
            PresentationStat.PLAY_TIME -> stats.playTimeMinutes.takeIf { it > 0 && !journeyShown }
                ?.let { RailItem(Icons.Default.SportsEsports, null, playedTemplate.format(formatPlayTime(context, it))) }
            PresentationStat.TIME_TO_BEAT -> formatTimeToBeat(context, stats.timeToBeatMainSec)
                ?.takeIf { !journeyShown }
                ?.let { RailItem(Icons.Default.Schedule, null, it) }
            PresentationStat.ACHIEVEMENTS -> stats.achievementCount.takeIf {
                it > 0 && style.layout != PresentationLayout.JOURNAL
            }?.let { RailItem(Icons.Filled.EmojiEvents, ALauncherColors.TrophyAmber, "${stats.earnedAchievementCount}/$it") }
            PresentationStat.FRIENDS -> null
        }?.let { stat to it }
    }
}

@Composable
private fun ShowcaseRail(rows: List<List<RailItem>>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        rows.forEach { ShowcaseRailRow(it) }
    }
}

@Composable
private fun ShowcaseRailRow(
    items: List<RailItem>,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    textColor: Color = LocalArgosyTheme.current.textPrimary
) {
    val theme = LocalArgosyTheme.current
    DividedFlow(
        count = items.size,
        lineSpacing = Dimens.spacingXs,
        divider = {
            Box(
                modifier = Modifier
                    .padding(horizontal = Dimens.spacingMd)
                    .width(Dimens.borderThin)
                    .height(Dimens.iconSm)
                    .background(theme.textPrimary.copy(alpha = DIVIDER_ALPHA))
            )
        }
    ) { index ->
        val item = items[index]
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = item.tint ?: textColor,
                    modifier = Modifier.padding(end = Dimens.spacingXs).size(Dimens.iconSm)
                )
            }
            Text(
                text = item.text,
                style = textStyle,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DividedFlow(
    count: Int,
    lineSpacing: Dp,
    divider: @Composable () -> Unit,
    item: @Composable (Int) -> Unit
) {
    Layout(
        contents = listOf(
            { repeat(count) { item(it) } },
            { repeat((count - 1).coerceAtLeast(0)) { divider() } }
        )
    ) { (itemMeasurables, dividerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val items = itemMeasurables.map { it.measure(loose) }
        val dividers = dividerMeasurables.map { it.measure(loose) }
        val spacing = lineSpacing.roundToPx()

        val lines = mutableListOf(mutableListOf(0))
        var lineWidth = items.firstOrNull()?.width ?: 0
        for (index in 1 until items.size) {
            val joined = lineWidth + dividers[index - 1].width + items[index].width
            if (joined <= constraints.maxWidth) {
                lines.last() += index
                lineWidth = joined
            } else {
                lines += mutableListOf(index)
                lineWidth = items[index].width
            }
        }
        val lineHeights = lines.map { line -> line.maxOf { items[it].height } }
        val width = if (items.isEmpty()) 0 else lines.maxOf { line ->
            line.sumOf { items[it].width } + line.drop(1).sumOf { dividers[it - 1].width }
        }.coerceAtMost(constraints.maxWidth)
        val height = lineHeights.sum() + spacing * (lines.size - 1).coerceAtLeast(0)

        layout(width, height) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                val lineHeight = lineHeights[lineIndex]
                var x = 0
                line.forEachIndexed { position, index ->
                    if (position > 0) {
                        val gap = dividers[index - 1]
                        gap.place(x, y + (lineHeight - gap.height) / 2)
                        x += gap.width
                    }
                    items[index].place(x, y + (lineHeight - items[index].height) / 2)
                    x += items[index].width
                }
                y += lineHeight + spacing
            }
        }
    }
}

private data class Journey(val fraction: Float, val played: String, val mainStory: String)

@Composable
private fun rememberJourney(stats: CompanionGameStats?, style: PresentationStyle): Journey? {
    stats ?: return null
    if (!style.shows(PresentationStat.PLAY_TIME) || !style.shows(PresentationStat.TIME_TO_BEAT)) return null
    val mainSec = stats.timeToBeatMainSec?.takeIf { it > 0 } ?: return null
    if (stats.playTimeMinutes <= 0) return null
    val context = LocalContext.current
    val mainStory = formatTimeToBeat(context, mainSec) ?: return null
    return Journey(
        fraction = (stats.playTimeMinutes * SECONDS_PER_MINUTE / mainSec.toFloat()).coerceIn(0f, 1f),
        played = formatPlayTime(context, stats.playTimeMinutes),
        mainStory = mainStory
    )
}

private const val SECONDS_PER_MINUTE = 60f

@Composable
private fun ShowcaseJourneyBar(journey: Journey) {
    ShowcaseProgressBar(
        fraction = journey.fraction,
        color = LocalArgosyTheme.current.focusAccent,
        label = stringResource(R.string.dual_showcase_progress_main, journey.played, journey.mainStory),
        icon = Icons.Default.SportsEsports
    )
}

@Composable
private fun ShowcaseProgressBar(fraction: Float, color: Color, label: String, icon: ImageVector) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.spacingXs)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(theme.textPrimary.copy(alpha = TRACK_ALPHA))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textDim,
            maxLines = 1
        )
    }
}
