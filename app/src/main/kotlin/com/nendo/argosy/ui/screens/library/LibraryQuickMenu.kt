package com.nendo.argosy.ui.screens.library

enum class LibraryQuickMenuRow(val isDangerous: Boolean = false) {
    PRIMARY,
    FAVORITE,
    DETAILS,
    ADD_TO_COLLECTION,
    ADD_TO_GRID,
    ACTIVE_VARIANT,
    REFRESH,
    RESYNC_PLATFORM,
    DELETE(isDangerous = true),
    HIDE(isDangerous = true)
}

fun libraryQuickMenuRows(
    game: LibraryGameUi,
    isCustomGridHome: Boolean,
    hasSiblingGroup: Boolean
): List<LibraryQuickMenuRow> = buildList {
    add(LibraryQuickMenuRow.PRIMARY)
    add(LibraryQuickMenuRow.FAVORITE)
    add(LibraryQuickMenuRow.DETAILS)
    add(LibraryQuickMenuRow.ADD_TO_COLLECTION)
    if (isCustomGridHome) add(LibraryQuickMenuRow.ADD_TO_GRID)
    if (hasSiblingGroup) add(LibraryQuickMenuRow.ACTIVE_VARIANT)
    if (game.isRommGame || game.isAndroidApp) add(LibraryQuickMenuRow.REFRESH)
    add(LibraryQuickMenuRow.RESYNC_PLATFORM)
    if (game.isDownloaded || game.needsInstall) add(LibraryQuickMenuRow.DELETE)
    add(LibraryQuickMenuRow.HIDE)
}
