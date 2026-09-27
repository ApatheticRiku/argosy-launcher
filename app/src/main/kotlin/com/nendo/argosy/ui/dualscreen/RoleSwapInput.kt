package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.DualScreenManagerHolder

/**
 * Whether select belongs to the role swap. A screen binding select to an action of its own
 * returns it unhandled while this is true, so the app-level handler performs the swap. While a game
 * runs it is true only when the game can follow the swap to its new display.
 */
fun selectSwapsRoles(): Boolean =
    DualScreenManagerHolder.instance
        ?.let {
            swapRule(
                it.isDualScreenDevice.value,
                it.hasPresentationScreen.value,
                it.swappedIsGameActive.value,
                it.liveSwapAvailable.value
            )
        } == true

/**
 * [selectSwapsRoles] for a footer, recomposing when any of its inputs changes.
 */
@Composable
fun selectSwapsRolesState(): Boolean {
    val manager = DualScreenManagerHolder.instance ?: return false
    val dualScreen by manager.isDualScreenDevice.collectAsState()
    val hasPresentation by manager.hasPresentationScreen.collectAsState()
    val gameActive by manager.swappedIsGameActive.collectAsState()
    val liveSwap by manager.liveSwapAvailable.collectAsState()
    return swapRule(dualScreen, hasPresentation, gameActive, liveSwap)
}

private fun swapRule(
    dualScreen: Boolean,
    hasPresentation: Boolean,
    gameActive: Boolean,
    liveSwap: Boolean
): Boolean = dualScreen && hasPresentation && (!gameActive || liveSwap)
