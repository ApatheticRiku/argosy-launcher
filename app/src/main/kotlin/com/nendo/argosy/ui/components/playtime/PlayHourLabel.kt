package com.nendo.argosy.ui.components.playtime

import android.content.Context
import com.nendo.argosy.util.formatClockTime
import java.time.LocalDate
import java.time.ZoneId

fun playHourLabel(context: Context, hour: Int): String {
    val millis = LocalDate.now().atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return formatClockTime(context, millis)
}
