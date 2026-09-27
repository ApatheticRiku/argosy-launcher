package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.ArtworkRow
import com.nendo.argosy.ui.screens.gamedetail.delegates.artworkRows

@Composable
fun ArtworkModal(
    game: GameDetailUi,
    focusIndex: Int,
    onRowClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = remember(game.overriddenArtSlots) { artworkRows(game.overriddenArtSlots) }
    val listState = rememberLazyListState()

    LaunchedEffect(focusIndex, rows.size) {
        listState.animateScrollToItemCentered(focusIndex)
    }

    Modal(
        title = stringResource(R.string.gamedetail_artwork_title),
        subtitle = game.title,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                OptionItem(
                    icon = row.icon,
                    label = stringResource(row.labelRes),
                    isFocused = index == focusIndex,
                    onClick = { onRowClick(index) }
                )
            }
        }
    }
}

private val ArtworkRow.key: String
    get() = when (this) {
        is ArtworkRow.Pick -> "pick_${slot.name}"
        is ArtworkRow.Revert -> "revert_${slot.name}"
    }

private val ArtworkRow.icon: ImageVector
    get() = when (this) {
        is ArtworkRow.Revert -> Icons.Default.Restore
        is ArtworkRow.Pick -> when (slot) {
            ArtSlot.COVER -> Icons.Default.Image
            ArtSlot.BACKGROUND -> Icons.Default.Wallpaper
            ArtSlot.LOGO -> Icons.Default.TextFields
        }
    }

@get:StringRes
private val ArtworkRow.labelRes: Int
    get() = when (this) {
        is ArtworkRow.Pick -> when (slot) {
            ArtSlot.COVER -> R.string.gamedetail_artwork_row_cover
            ArtSlot.BACKGROUND -> R.string.gamedetail_artwork_row_background
            ArtSlot.LOGO -> R.string.gamedetail_artwork_row_logo
        }
        is ArtworkRow.Revert -> when (slot) {
            ArtSlot.COVER -> R.string.gamedetail_artwork_row_revert_cover
            ArtSlot.BACKGROUND -> R.string.gamedetail_artwork_row_revert_background
            ArtSlot.LOGO -> R.string.gamedetail_artwork_row_revert_logo
        }
    }
