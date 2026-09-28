package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.SelectSwapMode

/**
 * The Select gesture that swaps screens right now, or null when no swap is available. A swap is
 * available on a dual-screen device with a presentation screen, and while a game runs only when
 * the game can follow the swap to its new display.
 */
fun activeSelectSwapMode(): SelectSwapMode? =
    DualScreenManagerHolder.instance?.let {
        swapRule(
            it.isDualScreenDevice.value,
            it.hasPresentationScreen.value,
            it.swappedIsGameActive.value,
            it.liveSwapAvailable.value,
            it.selectSwapMode.value
        )
    }

/**
 * Whether a press of Select belongs to the role swap. A screen binding Select to an action of its
 * own returns it unhandled while this is true, so the app-level handler performs the swap.
 */
fun selectSwapsRoles(): Boolean = activeSelectSwapMode() == SelectSwapMode.TAP

/**
 * Whether holding Select swaps the roles while a press keeps the screen's own Select action.
 */
fun selectHoldSwapsRoles(): Boolean = activeSelectSwapMode() == SelectSwapMode.HOLD

/**
 * [activeSelectSwapMode] for a footer, recomposing when any of its inputs changes.
 */
@Composable
fun selectSwapModeState(): SelectSwapMode? {
    val manager = DualScreenManagerHolder.instance ?: return null
    val dualScreen by manager.isDualScreenDevice.collectAsState()
    val hasPresentation by manager.hasPresentationScreen.collectAsState()
    val gameActive by manager.swappedIsGameActive.collectAsState()
    val liveSwap by manager.liveSwapAvailable.collectAsState()
    val mode by manager.selectSwapMode.collectAsState()
    return swapRule(dualScreen, hasPresentation, gameActive, liveSwap, mode)
}

private fun swapRule(
    dualScreen: Boolean,
    hasPresentation: Boolean,
    gameActive: Boolean,
    liveSwap: Boolean,
    mode: SelectSwapMode
): SelectSwapMode? = mode.takeIf { dualScreen && hasPresentation && (!gameActive || liveSwap) }
