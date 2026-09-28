package com.nendo.argosy.data.preferences

/**
 * Ordered region preference deciding which regional copy of a game is shown. Every list this
 * returns holds each of [SyncFilterPreferences.ALL_KNOWN_REGIONS] exactly once.
 */
object RegionPriority {

    fun seed(filters: SyncFilterPreferences): List<String> {
        val leading = if (filters.regionMode == RegionFilterMode.INCLUDE) {
            filters.enabledRegions
        } else {
            emptyList()
        }
        return normalize(leading)
    }

    fun normalize(order: List<String>): List<String> {
        val known = SyncFilterPreferences.ALL_KNOWN_REGIONS
        val ranked = order.filter { it in known }.distinct()
        return ranked + known.filterNot { it in ranked }
    }

    fun serialize(order: List<String>): String = normalize(order).joinToString(SEPARATOR)

    fun deserialize(stored: String): List<String> =
        normalize(stored.split(SEPARATOR).filter { it.isNotBlank() })

    private const val SEPARATOR = ","
}
