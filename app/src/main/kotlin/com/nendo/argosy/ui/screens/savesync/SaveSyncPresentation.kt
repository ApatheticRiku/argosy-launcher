package com.nendo.argosy.ui.screens.savesync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.model.GameActivitySnapshot
import com.nendo.argosy.domain.model.DeviceKind
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.components.playtime.playHourLabel
import com.nendo.argosy.ui.components.playtime.waveformPeak
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SaveGameDevice
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTimeVerbose
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val MS_PER_MINUTE = 60_000L
private const val PERCENT = 100L
private const val PRESENTED_DEVICES = 6
private const val SUBTITLE_SEPARATOR = " · "

@Composable
internal fun SaveSyncPresentation(viewModel: SaveSyncViewModel) {
    val active = DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    LaunchedEffect(active) { viewModel.setPresentationActive(active) }
    if (!active) return
    val activity by viewModel.gameActivity.collectAsState()
    val snapshot = activity ?: return
    PresentOnCompanion(SlotOwner("savesync.game"), snapshot.toSlot())
}

@Composable
private fun GameActivitySnapshot.toSlot(): PresentationSlot.SaveGame {
    val context = LocalContext.current
    val theme = LocalArgosyTheme.current
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val subtitle = buildList {
        add(platformSlug)
        if (sessionCount > 0) {
            add(pluralStringResource(R.plurals.savesync_presentation_sessions, sessionCount, sessionCount))
            add(stringResource(R.string.savesync_presentation_average, minutesLabel(totalActiveMs / sessionCount)))
            add(stringResource(R.string.savesync_presentation_longest, minutesLabel(longestSessionMs)))
        }
        lastPlayed?.let {
            add(stringResource(R.string.savesync_presentation_last_played, formatRelativeTimeVerbose(context, it)))
        }
        if (versionCount > 0) {
            add(pluralStringResource(R.plurals.savesync_presentation_versions, versionCount, versionCount))
        }
    }.joinToString(SUBTITLE_SEPARATOR)
    val peak = remember(weekHourMs) { waveformPeak(weekHourMs) }
    val peakLabel = peak?.let { (row, hour) ->
        stringResource(
            R.string.savesync_presentation_peak,
            DayOfWeek.of(row + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            playHourLabel(context, hour)
        )
    }
    val saveDayIndices = remember(days, saveDates) {
        days.indices.filter { days[it].date in saveDates }.toSet()
    }
    val totalMs = devices.sumOf { it.activeMs }.coerceAtLeast(1L)
    val presented = devices.take(PRESENTED_DEVICES).mapIndexed { index, device ->
        val name = DeviceKind.withoutRepeatedManufacturer(device.deviceName)
        SaveGameDevice(
            label = name,
            kind = DeviceKind.classify(device.deviceName, "android", null),
            activeMs = device.activeMs,
            valueLabel = minutesLabel(device.activeMs),
            shareLabel = stringResource(R.string.savesync_presentation_share_percent, (device.activeMs * PERCENT / totalMs).toInt()),
            detail = listOf(
                pluralStringResource(R.plurals.savesync_presentation_device_sessions, device.sessionCount, device.sessionCount),
                stringResource(R.string.savesync_presentation_device_last_played, formatRelativeTimeVerbose(context, device.lastPlayed))
            ).joinToString(SUBTITLE_SEPARATOR),
            color = series[index % series.size],
            isThisDevice = device.isThisDevice
        )
    }
    return PresentationSlot.SaveGame(
        title = title,
        subtitle = subtitle,
        totalLabel = totalActiveMs.takeIf { it >= MS_PER_MINUTE }?.let { minutesLabel(it) },
        coverPath = coverPath,
        backgroundPath = backgroundPath,
        days = days,
        saveDayIndices = saveDayIndices,
        weekHourMs = weekHourMs,
        peakLabel = peakLabel,
        devices = presented
    )
}

@Composable
private fun minutesLabel(ms: Long): String =
    formatPlayTime(LocalContext.current, (ms / MS_PER_MINUTE).toInt())
