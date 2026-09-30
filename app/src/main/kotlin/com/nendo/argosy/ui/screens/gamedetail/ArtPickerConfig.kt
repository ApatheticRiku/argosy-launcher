package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.model.ArtSlot

/**
 * How the artwork picker lays out and sources candidates for one slot. [searchNeedsCoverSearch]
 * marks the slot whose online search only runs when the server reports cover search support.
 */
data class ArtPickerConfig(
    val columns: Int,
    val tileAspectRatio: Float,
    val cropsToTile: Boolean,
    val checkeredBackdrop: Boolean,
    val offersScreenshots: Boolean,
    val searchNeedsCoverSearch: Boolean
) {
    fun canSearch(serverSupportsCoverSearch: Boolean): Boolean =
        !searchNeedsCoverSearch || serverSupportsCoverSearch
}

val ArtSlot.pickerConfig: ArtPickerConfig
    get() = when (this) {
        ArtSlot.COVER -> ArtPickerConfig(
            columns = 3,
            tileAspectRatio = 2f / 3f,
            cropsToTile = true,
            checkeredBackdrop = false,
            offersScreenshots = false,
            searchNeedsCoverSearch = true
        )
        ArtSlot.BACKGROUND -> ArtPickerConfig(
            columns = 2,
            tileAspectRatio = 16f / 9f,
            cropsToTile = false,
            checkeredBackdrop = false,
            offersScreenshots = true,
            searchNeedsCoverSearch = false
        )
        ArtSlot.LOGO -> ArtPickerConfig(
            columns = 3,
            tileAspectRatio = 16f / 9f,
            cropsToTile = false,
            checkeredBackdrop = true,
            offersScreenshots = false,
            searchNeedsCoverSearch = false
        )
    }

fun ArtSlot.stepped(delta: Int): ArtSlot =
    ArtSlot.entries[(ordinal + delta).mod(ArtSlot.entries.size)]

/**
 * [candidates] as the picker lists them for [slot]: led by a tile that puts the server's art
 * back when the user has overridden the slot.
 */
fun withRevertTile(slot: ArtSlot, overridden: Set<ArtSlot>, candidates: List<ArtCandidate>): List<ArtCandidate> =
    if (slot in overridden) listOf(ArtCandidate(source = "revert:${slot.name}", isRevert = true)) + candidates else candidates

/**
 * Next focus index in a grid of [size] tiles laid out [columns] wide. A step within a row wraps
 * across the whole list; a step of a full row wraps to the same column at the other end.
 */
fun artGridStep(index: Int, delta: Int, size: Int, columns: Int): Int {
    if (size <= 0) return 0
    val target = index + delta
    if (kotlin.math.abs(delta) < columns) return target.mod(size)
    if (target in 0 until size) return target
    val column = index.mod(columns)
    if (delta > 0) return column.coerceAtMost(size - 1)
    val lastRowStart = ((size - 1) / columns) * columns
    val wrapped = lastRowStart + column
    return if (wrapped < size) wrapped else wrapped - columns
}
