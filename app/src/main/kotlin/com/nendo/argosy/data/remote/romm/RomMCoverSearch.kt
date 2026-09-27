package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.model.ArtSlot

enum class RomMCoverArtType(val wireName: String) {
    GRID("grid"),
    HERO("hero"),
    LOGO("logo");

    companion object {
        fun forSlot(slot: ArtSlot): RomMCoverArtType = when (slot) {
            ArtSlot.COVER -> GRID
            ArtSlot.BACKGROUND -> HERO
            ArtSlot.LOGO -> LOGO
        }
    }
}

/**
 * The still images in a cover search response that fit [artType], each with its full-resolution
 * [RomMCoverResource.url] filled in. Hero and logo results count only when tagged with the
 * requested art type; an untagged response yields none for them. Grid results are taken as-is.
 */
fun List<RomMCoverSearchResult>.usableResources(artType: RomMCoverArtType): List<RomMCoverResource> =
    filter { artType == RomMCoverArtType.GRID || it.artType == artType.wireName }
        .flatMap { it.resources.orEmpty() }
        .filterNot { it.isAnimated }
        .mapNotNull { resource -> resource.fullResUrl(artType)?.let { resource.copy(url = it) } }

private val RomMCoverResource.isAnimated: Boolean
    get() = type == ANIMATED_TYPE || thumb.isWebm() || url.isWebm()

private fun String?.isWebm(): Boolean =
    this?.substringBefore('?')?.endsWith(WEBM_EXTENSION, ignoreCase = true) == true

private fun RomMCoverResource.fullResUrl(artType: RomMCoverArtType): String? =
    url ?: thumb
        ?.takeIf { artType == RomMCoverArtType.GRID }
        ?.replace(GRID_THUMB_SEGMENT, GRID_FULL_SEGMENT)

private const val ANIMATED_TYPE = "animated"
private const val WEBM_EXTENSION = ".webm"
private const val GRID_THUMB_SEGMENT = "/thumb/"
private const val GRID_FULL_SEGMENT = "/grid/"
