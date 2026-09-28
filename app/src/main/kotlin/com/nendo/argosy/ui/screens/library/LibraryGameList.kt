package com.nendo.argosy.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.components.fastAnimateScrollToItem
import com.nendo.argosy.ui.components.gamelist.GameListRow
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens

@Composable
internal fun LibraryGameList(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    sidebarWidth: Dp,
    headerHeightPx: Int,
    footerHeightPx: Int,
    onGameSelect: (Long) -> Unit
) {
    val initialIndex = remember { viewModel.gameIndexToGridIndex(uiState.focusedIndex) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentPlatformIndex) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.games.size) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex > 0) {
            listState.scrollToItem(viewModel.gameIndexToGridIndex(uiState.focusedIndex))
        }
    }

    LaunchedEffect(uiState.focusedIndex, uiState.lastFocusMove) {
        if (uiState.lastFocusMove == null || uiState.games.isEmpty()) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) return@LaunchedEffect
        val listIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull { it.index == listIndex }?.size
            ?: layoutInfo.visibleItemsInfo.firstOrNull()?.size
            ?: return@LaunchedEffect
        val centeringOffset = (viewportHeight - headerHeightPx - footerHeightPx - itemHeight) / 2
        isProgrammaticScroll = true
        listState.animateScrollToItem(index = listIndex, scrollOffset = -centeringOffset)
        isProgrammaticScroll = false
    }

    LaunchedEffect(uiState.sectionJumpTrigger) {
        if (uiState.sectionJumpTrigger == 0 || uiState.games.isEmpty()) return@LaunchedEffect
        val listIndex = viewModel.gameIndexToGridIndex(uiState.focusedIndex)
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        if (viewportHeight == 0) {
            listState.scrollToItem(listIndex)
            return@LaunchedEffect
        }
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
        val centeringOffset = if (itemHeight > 0) {
            (viewportHeight - headerHeightPx - footerHeightPx - itemHeight) / 2
        } else {
            0
        }
        isProgrammaticScroll = true
        listState.fastAnimateScrollToItem(index = listIndex, scrollOffset = -centeringOffset)
        isProgrammaticScroll = false
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling && !isProgrammaticScroll) viewModel.enterTouchMode()
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = Dimens.spacingMd,
            end = Dimens.spacingMd + sidebarWidth,
            top = Dimens.headerHeightLg,
            bottom = Dimens.headerHeightLg
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = uiState.gridItems.size,
            key = { i ->
                when (val item = uiState.gridItems[i]) {
                    is LibraryGridItem.Header -> "header-${item.label}"
                    is LibraryGridItem.Game -> item.game.id
                }
            },
            contentType = { i ->
                when (uiState.gridItems[i]) {
                    is LibraryGridItem.Header -> "header"
                    is LibraryGridItem.Game -> "game"
                }
            }
        ) { index ->
            when (val item = uiState.gridItems[index]) {
                is LibraryGridItem.Header -> SectionDivider(label = item.label)
                is LibraryGridItem.Game -> {
                    val game = item.game
                    val indicator by remember(game.id) {
                        derivedStateOf { downloadIndicators.value[game.id] ?: GameDownloadIndicator.NONE }
                    }
                    GameListRow(
                        title = game.title,
                        platformSlug = game.platformSlug,
                        platformDisplayName = game.platformDisplayName,
                        coverPath = uiState.repairedCoverPaths[game.id] ?: game.coverPath,
                        details = game.listDetails,
                        isDownloaded = game.isDownloaded,
                        needsInstall = game.needsInstall,
                        isFocused = item.gameIndex == uiState.focusedIndex &&
                            (!uiState.isTouchMode || uiState.hasSelectedGame),
                        downloadIndicator = indicator,
                        friends = game.listDetails.igdbId
                            ?.let { uiState.friendsActivity[it.toInt()] }
                            .orEmpty(),
                        saveState = uiState.saveStates[game.id],
                        onCoverLoadFailed = { path -> viewModel.repairCoverImage(game.id, path) },
                        onClick = { viewModel.handleItemTap(item.gameIndex, onGameSelect) },
                        onLongClick = { viewModel.handleItemLongPress(item.gameIndex) }
                    )
                }
            }
        }
    }
}
