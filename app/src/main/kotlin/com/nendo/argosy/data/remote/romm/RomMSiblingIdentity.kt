package com.nendo.argosy.data.remote.romm

/**
 * How RomM's gallery groups and classifies a rom, mirrored from the server so the device agrees
 * with the web UI: the group key of `roms_handler.group_by_meta_id`, and the pre-release filename
 * rule of `models/base.PRERELEASE_FILENAME_TAGS`.
 */
object RomMSiblingIdentity {

    private const val HACK_TAG = "hack"
    private const val PATCHED_TAG_PREFIX = "patched-"
    private const val TRANSLATION_TAG = "translation"

    private val PRE_RELEASE_TAGS = listOf("demo", "beta", "proto", "sample", "kiosk", "preview")

    fun groupKey(rom: RomMRom): String? {
        val (source, id) = listOf(
            "igdb" to rom.igdbId,
            "ss" to rom.ssId,
            "moby" to rom.mobyId,
            "ra" to rom.raId,
            "hasheous" to rom.hasheousId,
            "launchbox" to rom.launchboxId,
            "tgdb" to rom.tgdbId,
            "flashpoint" to rom.flashpointId,
            "steam" to rom.steamId
        ).firstOrNull { it.second != null } ?: return null
        return "$source-${rom.platformId}-$id"
    }

    fun isTranslation(rom: RomMRom): Boolean =
        rom.tags.orEmpty().any { it.equals(TRANSLATION_TAG, ignoreCase = true) }

    fun isHack(rom: RomMRom): Boolean {
        if (isTranslation(rom)) return false
        return rom.tags.orEmpty().any {
            it.equals(HACK_TAG, ignoreCase = true) || it.startsWith(PATCHED_TAG_PREFIX, ignoreCase = true)
        }
    }

    fun isPreRelease(fileName: String?): Boolean {
        val name = fileName ?: return false
        return PRE_RELEASE_TAGS.any { name.contains("($it", ignoreCase = true) }
    }
}
