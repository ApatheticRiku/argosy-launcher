package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

enum class InGameMenuSection(val action: InGameMenuAction) {
    STATES(InGameMenuAction.ManageStates),
    ACHIEVEMENTS(InGameMenuAction.Achievements),
    CHEATS(InGameMenuAction.Cheats),
    SETTINGS(InGameMenuAction.Settings),
    WALKTHROUGH(InGameMenuAction.ViewWalkthrough)
}

val InGameMenuAction.broadSection: InGameMenuSection?
    get() = InGameMenuSection.entries.firstOrNull { it.action == this }

val InGameMenuAction.icon: ImageVector
    get() = when (this) {
        InGameMenuAction.SwapDisc -> Icons.Filled.Album
        InGameMenuAction.Resume -> Icons.Filled.PlayArrow
        InGameMenuAction.QuickSave -> Icons.Filled.Save
        InGameMenuAction.QuickLoad -> Icons.Filled.Restore
        InGameMenuAction.ManageStates -> Icons.Filled.Layers
        InGameMenuAction.Settings -> Icons.Filled.Settings
        InGameMenuAction.Cheats -> Icons.Filled.Code
        InGameMenuAction.Achievements -> Icons.Filled.EmojiEvents
        InGameMenuAction.ViewManual -> Icons.AutoMirrored.Filled.MenuBook
        InGameMenuAction.ViewWalkthrough -> Icons.Filled.Map
        InGameMenuAction.ToggleWalkthroughPanel -> Icons.Filled.ViewSidebar
        InGameMenuAction.Reset -> Icons.Filled.RestartAlt
        InGameMenuAction.Quit -> Icons.Filled.PowerSettingsNew
        InGameMenuAction.OpenToFriends -> Icons.Filled.Groups
        InGameMenuAction.InviteFriend -> Icons.Filled.PersonAdd
        InGameMenuAction.ClearReservation -> Icons.Filled.LockOpen
        InGameMenuAction.CloseNetplaySession -> Icons.Filled.LinkOff
        InGameMenuAction.CustomizeTouchControls -> Icons.Filled.TouchApp
        InGameMenuAction.ToggleSpeedrun -> Icons.Filled.Timer
        InGameMenuAction.SwapScreens -> Icons.Filled.SwapHoriz
    }

@Composable
internal fun InGameMenuRail(
    menuItems: List<Pair<Int, InGameMenuAction>>,
    openAction: InGameMenuAction?,
    railFocused: Boolean,
    isEnabled: (InGameMenuAction) -> Boolean,
    onSelect: (InGameMenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val openIndex = menuItems.indexOfFirst { it.second == openAction }

    LaunchedEffect(openIndex) {
        listState.animateScrollToItemCentered(openIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(vertical = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(
            items = menuItems,
            key = { _: Int, item: Pair<Int, InGameMenuAction> -> item.second.toString() }
        ) { _, item ->
            val (labelRes, action) = item
            InGameMenuRailIcon(
                icon = action.icon,
                label = stringResource(labelRes),
                isOpen = action == openAction,
                isFocused = railFocused && action == openAction,
                enabled = isEnabled(action),
                onClick = { onSelect(action) }
            )
        }
    }
}

@Composable
private fun InGameMenuRailIcon(
    icon: ImageVector,
    label: String,
    isOpen: Boolean,
    isFocused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val tint = when {
        !enabled -> theme.textDim.copy(alpha = RAIL_DISABLED_ALPHA)
        isOpen -> theme.focusAccent
        else -> theme.textDim
    }
    Box(
        modifier = Modifier
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = RailIndicators,
                selected = isOpen,
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

private val RailIndicators = FocusIndicators(fill = true, ring = true)

private const val RAIL_DISABLED_ALPHA = 0.4f
