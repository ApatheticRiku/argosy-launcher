package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTime

internal const val THUMB_ASPECT = 4f / 3f
private const val CHIP_FILL_ALPHA = 0.18f
private const val MUTED_ALPHA = 0.7f
private val TileFocus = FocusIndicators(ring = true, fill = true)

@Composable
internal fun snapshotRelativeTime(iso: String?): String? {
    val context = LocalContext.current
    return remember(iso) { iso?.let { formatRelativeTime(context, it) }?.takeIf { it.isNotEmpty() } }
}

@Composable
internal fun SnapshotDeviceUi.label(): String = when (this) {
    SnapshotDeviceUi.Unknown -> stringResource(R.string.save_channels_device_unknown)
    SnapshotDeviceUi.OtherUser -> stringResource(R.string.save_channels_device_other_user)
    is SnapshotDeviceUi.Named -> name
}

@Composable
internal fun SnapshotThumb(url: String?, fallbackPath: String?, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val model = rememberFileImageModel(url ?: fallbackPath)
    Box(
        modifier = modifier
            .aspectRatio(THUMB_ASPECT)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(theme.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                tint = theme.textMute,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}

@Composable
internal fun SnapshotChip(text: String, color: Color, outlined: Boolean = false) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .then(
                if (outlined) Modifier.border(Dimens.borderThin, color, shape)
                else Modifier.background(color.copy(alpha = CHIP_FILL_ALPHA))
            )
            .padding(horizontal = Dimens.spacingXs)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SnapshotChannelTile(
    tile: SnapshotTileUi,
    coverPath: String?,
    isFocused: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val time = snapshotRelativeTime(tile.savedAt)
    val subtitle = when {
        time != null && tile.device != null ->
            stringResource(R.string.save_channels_tile_subtitle, time, tile.device.label())
        time != null -> time
        else -> stringResource(R.string.save_channels_tile_subtitle_empty)
    }
    Column(
        modifier = Modifier
            .width(Dimens.saveChannelTileWidth)
            .argosyFocusIndicators(focused = isFocused, indicators = TileFocus, shape = shape)
            .clip(shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        SnapshotThumb(tile.thumbnailUrl, coverPath, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isExpanded) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (tile.isHardcore) {
                SnapshotChip(stringResource(R.string.save_channels_tile_chip_hardcore), semantic.warning)
            }
            if (tile.isShared) {
                val shared = if (tile.isOwn || tile.ownerName == null) {
                    stringResource(R.string.save_channels_tile_chip_shared)
                } else {
                    stringResource(R.string.save_channels_tile_chip_by_owner, tile.ownerName)
                }
                SnapshotChip(shared, semantic.success)
            }
            if (tile.hasOnlyOlderSaves) {
                SnapshotChip(stringResource(R.string.save_channels_tile_chip_no_snapshots), theme.textDim, outlined = true)
            }
            if (tile.isDeviceChannel) {
                SnapshotChip(stringResource(R.string.save_channels_tile_chip_this_device), theme.focusAccent)
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SnapshotCard(
    card: SnapshotCardUi,
    coverPath: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val muted = card.isBranch || card.isOlderClient
    val subtitle = if (card.isOlderClient) card.fileName.orEmpty() else (card.device ?: SnapshotDeviceUi.Unknown).label()
    Column(
        modifier = Modifier
            .width(Dimens.saveChannelCardWidth)
            .argosyFocusIndicators(focused = isFocused, indicators = TileFocus, shape = shape)
            .clip(shape)
            .border(
                width = if (card.isCurrent) Dimens.borderMedium else Dimens.borderThin,
                color = if (card.isCurrent) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        SnapshotThumb(card.thumbnailUrl, coverPath, Modifier.fillMaxWidth())
        Text(
            text = snapshotRelativeTime(card.savedAt).orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) theme.textDim else theme.textPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim.copy(alpha = if (muted) MUTED_ALPHA else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SnapshotCardBadges(card, semantic.warning)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SnapshotCardBadges(card: SnapshotCardUi, hardcoreColor: Color) {
    val theme = LocalArgosyTheme.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (card.isOlderClient) {
            SnapshotChip(stringResource(R.string.save_channels_card_badge_older_client), theme.textDim, outlined = true)
        }
        if (card.isCurrent) {
            SnapshotChip(stringResource(R.string.save_channels_card_badge_current), theme.focusAccent)
        }
        if (card.isBranch) {
            SnapshotChip(stringResource(R.string.save_channels_card_badge_branch), theme.textDim, outlined = true)
        }
        if (card.isPinned) {
            SnapshotChip(stringResource(R.string.save_channels_card_badge_pinned), theme.textPrimary)
        }
        if (card.isHardcore) {
            SnapshotChip(stringResource(R.string.save_channels_card_badge_hardcore), hardcoreColor)
        }
        card.snapshotId?.let {
            Text(
                text = stringResource(R.string.save_channels_card_caption_id, it),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute
            )
        }
    }
}

@Composable
internal fun SnapshotLoadMoreCard(isFocused: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    Box(
        modifier = Modifier
            .width(Dimens.saveChannelCardWidth)
            .aspectRatio(THUMB_ASPECT)
            .argosyFocusIndicators(focused = isFocused, indicators = TileFocus, shape = shape)
            .clip(shape)
            .border(Dimens.borderThin, theme.hairlineLow, shape)
            .clickableNoFocus(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSm), strokeWidth = Dimens.borderMedium)
        } else {
            Text(
                text = stringResource(R.string.save_channels_card_load_more),
                style = MaterialTheme.typography.labelLarge,
                color = theme.textPrimary
            )
        }
    }
}

@Composable
internal fun SnapshotBackupRow(
    backup: SnapshotBackupUi,
    coverPath: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clip(shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        SnapshotThumb(backup.thumbnailUrl, coverPath, Modifier.width(Dimens.saveSyncRowCover))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            snapshotRelativeTime(backup.savedAt)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
        }
        Text(
            text = stringResource(R.string.save_channels_backup_action_new_channel),
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) theme.focusAccent else theme.textDim
        )
    }
}
