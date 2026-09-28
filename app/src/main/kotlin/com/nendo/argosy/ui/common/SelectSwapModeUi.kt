package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode

@get:StringRes
val SelectSwapMode.labelRes: Int
    get() = when (this) {
        SelectSwapMode.HOLD -> R.string.settings_navigation_select_swap_hold
        SelectSwapMode.TAP -> R.string.settings_navigation_select_swap_tap
    }
