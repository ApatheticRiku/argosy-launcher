package com.nendo.argosy.libretro

import com.nendo.argosy.data.platform.PlatformDefinitions

data class CorePortDevice(val id: Int, val name: String)

object CorePortDeviceCatalog {

    const val PORT_COUNT = 4

    private const val RETRO_DEVICE_JOYPAD = 1

    private fun subclass(shift: Int, base: Int): Int = (shift shl 8) or base

    private val DOLPHIN_WII = listOf(
        CorePortDevice(RETRO_DEVICE_JOYPAD, "WiiMote (upright)"),
        CorePortDevice(subclass(2, RETRO_DEVICE_JOYPAD), "WiiMote (sideways)"),
        CorePortDevice(subclass(3, RETRO_DEVICE_JOYPAD), "WiiMote + Nunchuk"),
        CorePortDevice(subclass(4, RETRO_DEVICE_JOYPAD), "WiiMote + Classic Controller"),
        CorePortDevice(subclass(5, RETRO_DEVICE_JOYPAD), "WiiMote + Classic Controller Pro"),
        CorePortDevice(subclass(7, RETRO_DEVICE_JOYPAD), "WiiMote + MotionPlus"),
        CorePortDevice(subclass(8, RETRO_DEVICE_JOYPAD), "WiiMote + MotionPlus (sideways)"),
        CorePortDevice(subclass(9, RETRO_DEVICE_JOYPAD), "WiiMote + MotionPlus + Nunchuk"),
        CorePortDevice(subclass(10, RETRO_DEVICE_JOYPAD), "WiiMote + MotionPlus + Classic Controller"),
        CorePortDevice(subclass(11, RETRO_DEVICE_JOYPAD), "WiiMote + MotionPlus + Classic Controller Pro")
    )

    fun devicesFor(platformSlug: String): List<CorePortDevice> =
        when (PlatformDefinitions.getCanonicalSlug(platformSlug)) {
            "wii" -> DOLPHIN_WII
            else -> emptyList()
        }
}
