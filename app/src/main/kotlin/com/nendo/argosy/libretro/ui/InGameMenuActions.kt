package com.nendo.argosy.libretro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.graphics.vector.ImageVector

enum class InGameMenuSection(val action: InGameMenuAction) {
    STATES(InGameMenuAction.ManageStates),
    ACHIEVEMENTS(InGameMenuAction.Achievements),
    CHEATS(InGameMenuAction.Cheats),
    SETTINGS(InGameMenuAction.Settings),
    WALKTHROUGH(InGameMenuAction.ViewWalkthrough),
    NETPLAY(InGameMenuAction.Netplay)
}

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
        InGameMenuAction.Netplay -> Icons.Filled.Groups
        InGameMenuAction.CustomizeTouchControls -> Icons.Filled.TouchApp
        InGameMenuAction.ToggleSpeedrun -> Icons.Filled.Timer
        InGameMenuAction.SwapScreens -> Icons.Filled.SwapHoriz
    }
