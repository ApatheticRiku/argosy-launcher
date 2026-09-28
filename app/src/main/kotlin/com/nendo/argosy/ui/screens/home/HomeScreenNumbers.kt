package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.screens.home.delegates.GameMenuState

internal fun showsScreenNumbers(state: HomeUiState, menu: GameMenuState): Boolean =
    (menu.showGameMenu && state.gameMenuDisplays.size > 1) ||
        (state.appBarMenu?.rows?.count { it is AppMenuRow.OpenOnScreen } ?: 0) > 1
