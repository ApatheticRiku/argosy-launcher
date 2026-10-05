package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val ROW_MINE_HEADER = "mine_header"
private const val ROW_MINE_TILES = "mine_tiles"
private const val ROW_MINE_EMPTY = "mine_empty"
private const val ROW_BACKUPS_HEADER = "backups_header"
private const val ROW_BACKUP_PREFIX = "backup_"
private const val ROW_COMMUNITY_HEADER = "community_header"
private const val ROW_COMMUNITY_TILES = "community_tiles"
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
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item(key = ROW_MINE_HEADER) {
                SectionHeader(
                    title = stringResource(R.string.save_channels_section_mine_title),
                    actionLabel = stringResource(R.string.save_channels_section_mine_new).takeIf { state.canCreateChannel },
                    isActionFocused = state.isStopFocused(SnapshotStop.NewChannel),
                    onAction = actions::tapNewChannel
                )
            }
            if (state.mine.isEmpty()) {
                item(key = ROW_MINE_EMPTY) {
                    SnapshotMessage(stringResource(R.string.save_channels_section_mine_empty))
                }
            } else {
                item(key = ROW_MINE_TILES) {
                    TileStrip(state.mine, state, isMine = true, coverPath = coverPath, actions = actions)
                }
            }
            if (state.backups.isNotEmpty()) {
                item(key = ROW_BACKUPS_HEADER) {
                    SectionHeader(title = stringResource(R.string.save_channels_section_backups_title))
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
                    SectionHeader(title = stringResource(R.string.save_channels_section_community_title))
                }
                item(key = ROW_COMMUNITY_TILES) {
                    TileStrip(state.community, state, isMine = false, coverPath = coverPath, actions = actions)
                }
            }
        }
    }
}

private fun rowKeys(state: SnapshotViewState): List<String> = buildList {
    add(ROW_MINE_HEADER)
    add(if (state.mine.isEmpty()) ROW_MINE_EMPTY else ROW_MINE_TILES)
    if (state.backups.isNotEmpty()) {
        add(ROW_BACKUPS_HEADER)
        state.backups.forEach { add("$ROW_BACKUP_PREFIX${it.saveId}") }
    }
    if (state.community.isNotEmpty()) {
        add(ROW_COMMUNITY_HEADER)
        add(ROW_COMMUNITY_TILES)
    }
}

private fun stopKey(state: SnapshotViewState): String? =
    when (val stop = state.stop) {
        SnapshotStop.NewChannel -> ROW_MINE_HEADER
        SnapshotStop.MineTiles -> ROW_MINE_TILES
        SnapshotStop.CommunityTiles -> ROW_COMMUNITY_TILES
        SnapshotStop.Cards -> if (state.expanded?.isMine == false) ROW_COMMUNITY_TILES else ROW_MINE_TILES
        is SnapshotStop.Backup -> state.backups.getOrNull(stop.index)?.let { "$ROW_BACKUP_PREFIX${it.saveId}" }
    }

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    isActionFocused: Boolean = false,
    onAction: () -> Unit = {}
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            modifier = Modifier.weight(1f)
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
    }
}

@Composable
private fun TileStrip(
    tiles: List<SnapshotTileUi>,
    state: SnapshotViewState,
    isMine: Boolean,
    coverPath: String?,
    actions: SnapshotViewActions
) {
    val stripState = rememberLazyListState()
    val tileFocus = if (isMine) state.mineIndex else state.communityIndex
    val tileStop = if (isMine) SnapshotStop.MineTiles else SnapshotStop.CommunityTiles
    val open = state.expanded?.takeIf { it.isMine == isMine }
    val openIndex = open?.let { expanded -> tiles.indexOfFirst { it.channelId == expanded.channelId } } ?: -1
    val inlineCount = open?.let(::inlineItemCount) ?: 0
    val focusedItem = if (open != null && state.isStopFocused(SnapshotStop.Cards)) {
        openIndex + 1 + open.focusIndex
    } else {
        tileFocus + if (openIndex in 0 until tileFocus) inlineCount else 0
    }
    LaunchedEffect(focusedItem) { stripState.animateScrollToItemCentered(focusedItem) }
    LazyRow(
        state = stripState,
        contentPadding = PaddingValues(horizontal = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.Top
    ) {
        tiles.forEachIndexed { index, tile ->
            item(key = tile.channelId) {
                SnapshotChannelTile(
                    tile = tile,
                    coverPath = coverPath,
                    isFocused = state.isStopFocused(tileStop) && index == tileFocus,
                    isExpanded = index == openIndex,
                    onClick = { actions.tapTile(isMine, index) },
                    onLongClick = { actions.longPressTile(isMine, index) }
                )
            }
            if (index == openIndex && open != null) {
                inlineCards(open, state.isStopFocused(SnapshotStop.Cards), coverPath, actions)
            }
        }
    }
}

private fun inlineItemCount(open: SnapshotExpandedUi): Int = when {
    open.isLoading -> SKELETON_COUNT
    open.cards.isEmpty() -> 1
    else -> open.stopCount
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
            .background(LocalArgosyTheme.current.surfaceRaised)
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
