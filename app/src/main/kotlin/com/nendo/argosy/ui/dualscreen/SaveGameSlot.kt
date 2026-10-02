package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.SegmentedMeterBar
import com.nendo.argosy.ui.components.playtime.PlayCalendar
import com.nendo.argosy.ui.components.playtime.PlayWaveform
import com.nendo.argosy.ui.components.playtime.playHourLabel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val SAVE_GAME_SCRIM = 0.82f
private const val PANEL_ALPHA = 0.72f
private const val HOURS_IN_DAY = 24
private const val HOUR_LABEL_STEP = 6

@Composable
internal fun SaveGameSlot(slot: PresentationSlot.SaveGame, bottomInset: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        slot.backgroundPath?.let { backdrop ->
            AsyncImage(
                model = rememberFileImageModel(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = SAVE_GAME_SCRIM))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Dimens.spacingXl, end = Dimens.spacingXl, top = Dimens.spacingXl, bottom = bottomInset + Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            SaveGameHeader(slot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
            ) {
                SaveGamePanel(
                    title = stringResource(R.string.savesync_presentation_calendar_title),
                    modifier = Modifier.weight(1f)
                ) {
                    val accent = MaterialTheme.colorScheme.primary
                    val daySlots = remember(slot.days) { List(slot.days.size) { 0 } }
                    PlayCalendar(
                        days = slot.days,
                        daySlots = daySlots,
                        seriesColors = listOf(accent),
                        othersColor = accent,
                        selectedIndex = null,
                        showSelection = false,
                        onCellTap = {},
                        markedIndices = slot.saveDayIndices
                    )
                    SaveMarkerLegend()
                }
                SaveGamePanel(
                    title = stringResource(R.string.savesync_presentation_rhythm_title),
                    modifier = Modifier.weight(1f)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val weekdays = remember { DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } }
                    val hours = remember(context) {
                        (0 until HOURS_IN_DAY step HOUR_LABEL_STEP).map { playHourLabel(context, it) }
                    }
                    PlayWaveform(
                        weekHourMs = slot.weekHourMs,
                        weekdayLabels = weekdays,
                        hourLabels = hours,
                        peakLabel = slot.peakLabel,
                        selectedWeekday = null,
                        selectedHour = null,
                        onCellTap = { _, _ -> }
                    )
                }
                if (slot.devices.isNotEmpty()) {
                    SaveGamePanel(
                        title = stringResource(R.string.savesync_presentation_devices_title),
                        modifier = Modifier.weight(1f)
                    ) {
                        SegmentedMeterBar(
                            totalBytes = slot.totalDeviceMs,
                            segments = slot.deviceSegments,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        slot.devices.forEach { device -> SaveGameDeviceRow(device) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveGameHeader(slot: PresentationSlot.SaveGame) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(Dimens.saveSyncAttentionCover)
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceBase)
        ) {
            AsyncImage(
                model = rememberFileImageModel(slot.coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Text(
                text = slot.title,
                style = MaterialTheme.typography.displaySmall,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = slot.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textDim,
                maxLines = 2
            )
        }
        slot.totalLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium,
                color = theme.focusAccent
            )
        }
    }
}

@Composable
private fun SaveGamePanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(theme.surfaceRaised.copy(alpha = PANEL_ALPHA))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textDim,
            maxLines = 1
        )
        content()
    }
}

@Composable
private fun SaveMarkerLegend() {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.dotSm)
                .clip(CircleShape)
                .background(theme.textPrimary)
        )
        Text(
            text = stringResource(R.string.savesync_presentation_save_marker),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun SaveGameDeviceRow(device: SaveGameDevice, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(modifier = Modifier.size(Dimens.dotSm).clip(CircleShape).background(device.color))
        Icon(
            painter = device.kind.icon,
            contentDescription = null,
            tint = theme.textPrimary,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                Text(
                    text = device.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (device.isThisDevice) {
                    Text(
                        text = stringResource(R.string.savesync_presentation_this_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.radiusPill))
                            .background(theme.surfaceBase)
                            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
                    )
                }
            }
            Text(
                text = device.detail,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = device.valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
        Text(
            text = device.shareLabel,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
            maxLines = 1
        )
    }
}
