package com.nendo.argosy.domain.model

enum class DeviceKind {
    HANDHELD_HORIZONTAL,
    HANDHELD_VERTICAL,
    DUAL_SCREEN,
    PHONE,
    TABLET,
    TV,
    DESKTOP,
    WEB,
    UNKNOWN;

    companion object {
        private val DUAL_SCREEN_MARKERS = listOf("thor", "flip ds", "dual screen")
        private val VERTICAL_MARKERS = listOf(
            "pocket classic", "pocket dmg", "rg353v", "rg405v", "rg406v", "rg28xx", "rg35xx plus", "rg35xxplus"
        )
        private val HANDHELD_MARKERS = listOf(
            "ayn", "odin", "retroid", "moorechip", "ayaneo", "konkr", "anbernic", "gpd", "steam deck",
            "rog ally", "legion go", "msi claw", "pocket fit", "rp5", "rp6", "rpmini"
        )
        private val TV_MARKERS = listOf("shield", "chromecast", "fire tv", "bravia", "google tv", "android tv")
        private val TABLET_MARKERS = listOf("tablet", "ipad", "galaxy tab", " tab ", "pixel tablet")
        private val DESKTOP_PLATFORMS = listOf("linux", "windows", "mac", "darwin")

        fun classify(name: String?, platform: String?, client: String?, isWeb: Boolean = false): DeviceKind {
            val lowerName = " ${name.orEmpty().lowercase().trim()} "
            val lowerPlatform = platform.orEmpty().lowercase().trim()
            val lowerClient = client.orEmpty().lowercase().trim()
            return when {
                isWeb || lowerPlatform == "web" || lowerPlatform == "browser" -> WEB
                DUAL_SCREEN_MARKERS.any { it in lowerName } -> DUAL_SCREEN
                VERTICAL_MARKERS.any { it in lowerName } -> HANDHELD_VERTICAL
                HANDHELD_MARKERS.any { it in lowerName } -> HANDHELD_HORIZONTAL
                "tv" in lowerPlatform || TV_MARKERS.any { it in lowerName } -> TV
                TABLET_MARKERS.any { it in lowerName } -> TABLET
                DESKTOP_PLATFORMS.any { it in lowerPlatform } -> DESKTOP
                lowerPlatform == "android" || lowerPlatform == "ios" || lowerClient == "argosy" -> PHONE
                else -> UNKNOWN
            }
        }

        fun withoutRepeatedManufacturer(name: String): String {
            val trimmed = name.trim()
            val first = trimmed.substringBefore(' ', missingDelimiterValue = "")
            if (first.isEmpty()) return trimmed
            val rest = trimmed.substring(first.length).trim()
            return if (rest.startsWith(first, ignoreCase = true)) rest else trimmed
        }
    }
}
