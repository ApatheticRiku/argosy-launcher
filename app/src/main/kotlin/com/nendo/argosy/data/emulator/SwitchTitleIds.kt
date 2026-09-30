package com.nendo.argosy.data.emulator

/**
 * Switch title-id layout, per nxdumptool include/core/title.h: a patch is `application + 0x800`
 * and add-on content is `(application & ~0xFFF) + 0x1000 + index`. Saves are keyed by the
 * application id alone.
 */
object SwitchTitleIds {
    private const val HEX_LENGTH = 16
    private const val HEX_RADIX = 16
    private val APPLICATION_RANGE = 0x0100000000010000uL..0x01FFFFFFFFFFFFFFuL
    private const val TYPE_BITS = 0xFFFuL
    private const val PATCH_OFFSET = 0x800uL
    private const val ADD_ON_CONTENT_OFFSET = 0x1000uL

    /**
     * The application id [titleId] belongs to. A patch or add-on content id maps to its
     * application, uppercase; an application id, or anything outside the application range, comes
     * back unchanged.
     */
    fun baseApplicationId(titleId: String): String {
        if (titleId.length != HEX_LENGTH) return titleId
        val value = titleId.toULongOrNull(HEX_RADIX)?.takeIf { it in APPLICATION_RANGE } ?: return titleId
        val base = when {
            (value and ADD_ON_CONTENT_OFFSET) != 0uL -> (value and TYPE_BITS.inv()) - ADD_ON_CONTENT_OFFSET
            (value and TYPE_BITS) == PATCH_OFFSET -> value - PATCH_OFFSET
            else -> return titleId
        }
        return base.toString(HEX_RADIX).uppercase().padStart(HEX_LENGTH, '0')
    }
}
