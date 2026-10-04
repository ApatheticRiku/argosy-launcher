package com.nendo.argosy.data.model

/**
 * A game's art after [resolveArtPath] has picked one path per slot. Gradient and aspect ratio come
 * from the cover row.
 */
data class ResolvedGameArt(
    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val logoPath: String? = null,
    val gradientColors: String? = null,
    val coverAspectRatio: Float? = null,
    val overriddenSlots: Set<ArtSlot> = emptySet()
) {
    fun path(slot: ArtSlot): String? = when (slot) {
        ArtSlot.COVER -> coverPath
        ArtSlot.BACKGROUND -> backgroundPath
        ArtSlot.LOGO -> logoPath
    }

    companion object {
        val EMPTY = ResolvedGameArt()
    }
}

/**
 * The one art resolution rule: a user override wins, then the locally cached file, then the remote
 * source url. `RESOLVED_ART_SQL` in the dao package is its SQL twin.
 */
fun resolveArtPath(overridePath: String?, cachedPath: String?, sourceUrl: String?): String? =
    overridePath ?: cachedPath ?: sourceUrl
