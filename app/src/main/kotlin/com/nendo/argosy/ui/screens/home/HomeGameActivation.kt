package com.nendo.argosy.ui.screens.home

enum class HomeGameActivation {
    INSTALL,
    LAUNCH,
    RESUME_DOWNLOAD,
    STEAM_DOWNLOAD,
    DOWNLOAD_EXACT,
    DOWNLOAD_WITH_CHOICE
}

/**
 * What pressing [game] does. [exactRow] marks a press on a surface bound to this exact rom, such as
 * a curated grid tile, which downloads that rom without offering its sibling group.
 */
fun homeGameActivation(
    game: HomeGameUi,
    indicator: GameDownloadIndicator,
    exactRow: Boolean
): HomeGameActivation = when {
    game.needsInstall -> HomeGameActivation.INSTALL
    game.isDownloaded -> HomeGameActivation.LAUNCH
    indicator.isPaused || indicator.isQueued -> HomeGameActivation.RESUME_DOWNLOAD
    game.isSteamGame -> HomeGameActivation.STEAM_DOWNLOAD
    exactRow -> HomeGameActivation.DOWNLOAD_EXACT
    else -> HomeGameActivation.DOWNLOAD_WITH_CHOICE
}
