package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val ROW_MINE_HEADER = "mine_header"
private const val ROW_MINE_PREFIX = "mine_"
private const val ROW_MINE_EMPTY = "mine_empty"
private const val ROW_BACKUPS_HEADER = "backups_header"
private const val ROW_BACKUP_PREFIX = "backup_"
private const val ROW_COMMUNITY_HEADER = "community_header"
private const val ROW_COMMUNITY_PREFIX = "community_"
private const val SKELETON_COUNT = 3

@Composable
internal fun SnapshotChannelView(
    state: SnapshotViewState,
    coverPath: String?,
    maxHeight: Dp,
    actions: SnapshotViewActions
) {
    when {
        state.isLoading -> SnapshotSkeleton()
        state.loadFailed -> SnapshotMessage(stringResource(R.string.save_channels_load_failed))
        else -> SnapshotSections(state, coverPath, maxHeight, actions)
    }
}

@Composable
private fun SnapshotSections(
    state: SnapshotViewState,
    coverPath: String?,
    maxHeight: Dp,
    actions: SnapshotViewActions
) {
    val listState = rememberLazyListState()
    val focusedRow = stopKey(state)?.let { rowKeys(state).indexOf(it) } ?: -1
    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) listState.animateScrollToItemCentered(focusedRow)
    }
    Column {
        if (state.isBusy) {
            ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            item(key = ROW_MINE_HEADER) {
                SectionHeader(
                    title = stringResource(R.string.save_channels_section_mine_title),
                    actionLabel = stringResource(R.string.save_channels_section_mine_new).takeIf { state.canCreateChannel },
                    isActionFocused = state.isStopFocused(SnapshotStop.NewChannel),
                    onAction = actions::tapNewChannel,
                    onTimeline = actions::openTimeline
                )
            }
            if (state.mine.isEmpty()) {
                item(key = ROW_MINE_EMPTY) {
                    SnapshotMessage(stringResource(R.string.save_channels_section_mine_empty))
                }
            } else {
                itemsIndexed(state.mine, key = { _, tile -> "$ROW_MINE_PREFIX${tile.channelId}" }) { index, tile ->
                    ChannelRow(tile, index, state, isMine = true, coverPath = coverPath, actions = actions)
                }
            }
            if (state.backups.isNotEmpty()) {
                item(key = ROW_BACKUPS_HEADER) {
                    SectionHeader(
                        title = stringResource(R.string.save_channels_section_backups_title),
                        modifier = Modifier.padding(top = Dimens.spacingMd)
                    )
                }
                itemsIndexed(state.backups, key = { _, backup -> "$ROW_BACKUP_PREFIX${backup.saveId}" }) { index, backup ->
                    SnapshotBackupRow(
                        backup = backup,
                        coverPath = coverPath,
                        isFocused = state.isStopFocused(SnapshotStop.Backup(index)),
                        onClick = { actions.tapBackup(index) }
                    )
                }
            }
            if (state.community.isNotEmpty()) {
                item(key = ROW_COMMUNITY_HEADER) {
                    SectionHeader(
                        title = stringResource(R.string.save_channels_section_community_title),
                        modifier = Modifier.padding(top = Dimens.spacingMd)
                    )
                }
                itemsIndexed(state.community, key = { _, tile -> "$ROW_COMMUNITY_PREFIX${tile.channelId}" }) { index, tile ->
                    ChannelRow(tile, index, state, isMine = false, coverPath = coverPath, actions = actions)
                }
            }
        }
    }
}

private fun rowKeys(state: SnapshotViewState): List<String> = buildList {
    add(ROW_MINE_HEADER)
    if (state.mine.isEmpty()) add(ROW_MINE_EMPTY)
    state.mine.forEach { add("$ROW_MINE_PREFIX${it.channelId}") }
    if (state.backups.isNotEmpty()) {
        add(ROW_BACKUPS_HEADER)
        state.backups.forEach { add("$ROW_BACKUP_PREFIX${it.saveId}") }
    }
    if (state.community.isNotEmpty()) {
        add(ROW_COMMUNITY_HEADER)
        state.community.forEach { add("$ROW_COMMUNITY_PREFIX${it.channelId}") }
    }
}

private fun stopKey(state: SnapshotViewState): String? =
    when (val stop = state.stop) {
        SnapshotStop.NewChannel -> ROW_MINE_HEADER
        SnapshotStop.MineTiles -> state.mine.getOrNull(state.mineIndex)?.let { "$ROW_MINE_PREFIX${it.channelId}" }
        SnapshotStop.CommunityTiles ->
            state.community.getOrNull(state.communityIndex)?.let { "$ROW_COMMUNITY_PREFIX${it.channelId}" }
        SnapshotStop.Cards -> state.expanded?.let { open ->
            (if (open.isMine) ROW_MINE_PREFIX else ROW_COMMUNITY_PREFIX) + open.channelId
        }
        is SnapshotStop.Backup -> state.backups.getOrNull(stop.index)?.let { "$ROW_BACKUP_PREFIX${it.saveId}" }
    }

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    isActionFocused: Boolean = false,
    onAction: () -> Unit = {},
    onTimeline: (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.borderThin)
                .background(theme.hairlineHigh)
        )
        if (actionLabel != null) {
            ModalActionButton(
                label = actionLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = isActionFocused,
                onClick = onAction
            )
        }
        if (onTimeline != null) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = stringResource(R.string.save_channels_section_timeline_button),
                tint = theme.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .clickableNoFocus(onClick = onTimeline)
                    .padding(Dimens.spacingXs)
                    .size(Dimens.iconMd)
            )
        }
    }
}

@Composable
private fun ChannelRow(
    tile: SnapshotTileUi,
    index: Int,
    state: SnapshotViewState,
    isMine: Boolean,
    coverPath: String?,
    actions: SnapshotViewActions
) {
    val rowState = rememberLazyListState()
    val tileFocus = if (isMine) state.mineIndex else state.communityIndex
    val tileStop = if (isMine) SnapshotStop.MineTiles else SnapshotStop.CommunityTiles
    val open = state.expanded?.takeIf { it.isMine == isMine && it.channelId == tile.channelId }
    val cardsFocused = open != null && state.isStopFocused(SnapshotStop.Cards)
    val focusedItem = if (cardsFocused && open != null) 1 + open.focusIndex else 0
    LaunchedEffect(focusedItem) { rowState.animateScrollToItemCentered(focusedItem) }
    val theme = LocalArgosyTheme.current
    val laneShape = RoundedCornerShape(Dimens.radiusXl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(laneShape)
            .then(
                if (open != null) {
                    Modifier
                        .background(theme.surfaceRaised)
                        .border(Dimens.borderThin, theme.hairlineLow, laneShape)
                } else {
                    Modifier
                }
            )
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.Top
        ) {
            item(key = tile.channelId) {
                Box(modifier = Modifier.padding(end = Dimens.spacingXs)) {
                    SnapshotChannelTile(
                        tile = tile,
                        coverPath = coverPath,
                        isFocused = state.isStopFocused(tileStop) && index == tileFocus,
                        isExpanded = open != null,
                        onClick = { actions.tapTile(isMine, index) },
                        onLongClick = { actions.longPressTile(isMine, index) }
                    )
                }
            }
            if (open != null) inlineCards(open, cardsFocused, coverPath, actions)
        }
    }
}

private fun LazyListScope.inlineCards(
    open: SnapshotExpandedUi,
    focused: Boolean,
    coverPath: String?,
    actions: SnapshotViewActions
) {
    when {
        open.isLoading -> items(SKELETON_COUNT, key = { "${open.channelId}_skeleton_$it" }) { CardSkeleton() }
        open.cards.isEmpty() -> item(key = "${open.channelId}_empty") {
            Text(
                text = stringResource(R.string.save_channels_cards_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.width(Dimens.saveChannelCardWidth).padding(Dimens.spacingMd)
            )
        }
        else -> {
            itemsIndexed(open.cards, key = { _, card -> "${open.channelId}_${card.key}" }) { index, card ->
                SnapshotCard(
                    card = card,
                    coverPath = coverPath,
                    isFocused = focused && open.focusIndex == index,
                    onClick = { actions.tapCard(index) }
                )
            }
            if (open.hasMore) {
                item(key = "${open.channelId}_load_more") {
                    SnapshotLoadMoreCard(
                        isFocused = focused && open.isLoadMoreFocused,
                        isLoading = open.isLoadingMore,
                        onClick = { actions.tapCard(open.cards.size) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardSkeleton() {
    Box(
        modifier = Modifier
            .width(Dimens.saveChannelCardWidth)
            .aspectRatio(THUMB_ASPECT)
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(LocalArgosyTheme.current.surfaceElevated)
    )
}

@Composable
private fun SnapshotSkeleton() {
    val theme = LocalArgosyTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Box(
            modifier = Modifier
                .width(Dimens.saveChannelTileWidth)
                .height(Dimens.spacingMd)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            repeat(SKELETON_COUNT) {
                Box(
                    modifier = Modifier
                        .width(Dimens.saveChannelTileWidth)
                        .aspectRatio(THUMB_ASPECT)
                        .clip(RoundedCornerShape(Dimens.radiusLg))
                        .background(theme.surfaceRaised)
                )
            }
        }
    }
}

@Composable
private fun SnapshotMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.fillMaxWidth().padding(Dimens.spacingMd)
    )
}
