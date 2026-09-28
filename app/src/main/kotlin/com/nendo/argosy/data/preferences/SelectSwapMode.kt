package com.nendo.argosy.data.preferences

/**
 * The Select gesture that swaps screens on a dual-screen device. [HOLD] leaves a press of Select
 * its single-screen action; [TAP] gives the press to the swap.
 */
enum class SelectSwapMode {
    HOLD, TAP;

    companion object {
        fun fromString(value: String?): SelectSwapMode =
            entries.find { it.name == value } ?: HOLD
    }
}
