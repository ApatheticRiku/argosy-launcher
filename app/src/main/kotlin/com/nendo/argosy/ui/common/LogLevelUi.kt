package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.util.LogLevel

@get:StringRes
val LogLevel.labelRes: Int
    get() = when (this) {
        LogLevel.DEBUG -> R.string.settings_about_log_level_debug
        LogLevel.INFO -> R.string.settings_about_log_level_info
        LogLevel.WARN -> R.string.settings_about_log_level_warn
        LogLevel.ERROR -> R.string.settings_about_log_level_error
    }
