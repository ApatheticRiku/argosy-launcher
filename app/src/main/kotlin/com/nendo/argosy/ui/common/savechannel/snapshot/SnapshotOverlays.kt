package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatAbsoluteTimestamp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime

private val PICKER_MAX_HEIGHT = DimensionTokens.Layout.slotPickerListMaxHeight.dp

@Composable
internal fun SnapshotOverlays(state: SnapshotViewState, coverPath: String?, actions: SnapshotViewActions) {
    state.detail?.let { detail ->
        SnapshotDetailOverlay(
            detail = detail,
            coverPath = coverPath,
            isActive = state.copyPicker == null && state.labelEntry == null && state.confirm == null,
            actions = actions
        )
    }
    state.channelMenu?.let { menu ->
        NestedModal(title = menu.label, onDismiss = actions::closeOverlay) {
            menu.actions.forEachIndexed { index, action ->
                SnapshotOverlayRow(
                    label = action.label(),
                    isFocused = state.labelEntry == null && state.confirm == null && menu.focusIndex == index,
                    isDestructive = action == SnapshotChannelAction.DELETE,
                    onClick = { actions.tapOverlayRow(index) }
                )
            }
        }
    }
    state.copyPicker?.let { picker ->
        NestedModal(title = stringResource(R.string.save_channels_copy_picker_title), onDismiss = actions::closeOverlay) {
            LazyColumn(
                modifier = Modifier.heightIn(max = PICKER_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(picker.targets, key = { _, target -> target.channelId }) { index, target ->
                    SnapshotOverlayRow(
                        label = target.label,
                        isFocused = state.confirm == null && picker.focusIndex == index,
                        onClick = { actions.tapOverlayRow(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotDetailOverlay(
    detail: SnapshotDetailUi,
    coverPath: String?,
    isActive: Boolean,
    actions: SnapshotViewActions
) {
    val theme = LocalArgosyTheme.current
    val card = detail.card
    val time = snapshotRelativeTime(card.savedAt).orEmpty()
    val title = card.snapshotId?.let { stringResource(R.string.save_channels_detail_title, time, it) } ?: time
    NestedModal(title = title, baseWidth = Dimens.modalWidthXl, onDismiss = actions::closeOverlay) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                SnapshotThumb(
                    url = card.thumbnailUrl,
                    fallbackPath = coverPath,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Dimens.radiusLg)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    SnapshotCardBadges(card, LocalLauncherTheme.current.semanticColors.warning)
                    DetailRow(stringResource(R.string.save_channels_detail_row_channel), detail.channelLabel)
                    if (card.isOlderClient) {
                        DetailRow(stringResource(R.string.save_channels_detail_row_file), card.fileName.orEmpty())
                    } else {
                        DetailRow(
                            stringResource(R.string.save_channels_detail_row_device),
                            (card.device ?: SnapshotDeviceUi.Unknown).label()
                        )
                        DetailRow(
                            stringResource(R.string.save_channels_detail_row_parent),
                            detail.parentId?.let { stringResource(R.string.save_channels_detail_parent_id, it) }
                                ?: stringResource(R.string.save_channels_detail_parent_none)
                        )
                    }
                    absoluteTime(card.savedAt)?.let {
                        DetailRow(stringResource(R.string.save_channels_detail_row_saved), it)
                    }
                }
            }
            if (!card.isOlderClient) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusLg))
                        .background(theme.surfaceRaised)
                        .padding(Dimens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    SnapshotEyebrow(stringResource(R.string.save_channels_detail_states_title))
                    when {
                        detail.states.isNotEmpty() -> detail.states.forEach { core -> CoreStates(core) }
                        card.isHardcore -> StatesNote(stringResource(R.string.save_channels_detail_states_hardcore))
                        else -> StatesNote(stringResource(R.string.save_channels_detail_states_none))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                HorizontalDivider(color = theme.hairlineLow)
                detail.actions.forEachIndexed { index, action ->
                    SnapshotOverlayRow(
                        label = action.label(),
                        isFocused = isActive && detail.focusIndex == index,
                        onClick = { actions.tapOverlayRow(index) },
                        icon = action.icon
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        SnapshotEyebrow(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalArgosyTheme.current.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreStates(core: SnapshotCoreStatesUi) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = core.core,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            core.slots.forEach { slot ->
                SnapshotChip(
                    text = if (slot == AUTO_SLOT) stringResource(R.string.save_channels_detail_state_slot_auto)
                    else stringResource(R.string.save_channels_detail_state_slot_numbered, slot),
                    color = theme.textDim,
                    outlined = true
                )
            }
        }
    }
}

@Composable
private fun StatesNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = LocalArgosyTheme.current.textDim)
}

@Composable
internal fun SnapshotOverlayRow(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    icon: ImageVector? = null
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val labelColor = if (isDestructive) theme.destructive else theme.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clip(shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) labelColor else theme.textDim,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
    }
}

private val SnapshotDetailAction.icon: ImageVector
    get() = when (this) {
        SnapshotDetailAction.USE_ON_DEVICE -> Icons.Filled.PlayArrow
        SnapshotDetailAction.RESTORE -> Icons.Filled.Restore
        SnapshotDetailAction.FORK -> Icons.AutoMirrored.Filled.CallSplit
        SnapshotDetailAction.COPY_OVER -> Icons.Filled.ContentCopy
        SnapshotDetailAction.PIN -> Icons.Filled.PushPin
        SnapshotDetailAction.UNPIN -> Icons.Outlined.PushPin
        SnapshotDetailAction.MAKE_SNAPSHOT -> Icons.Filled.Layers
    }

@Composable
private fun SnapshotDetailAction.label(): String = stringResource(
    when (this) {
        SnapshotDetailAction.USE_ON_DEVICE -> R.string.save_channels_detail_action_use_on_device
        SnapshotDetailAction.RESTORE -> R.string.save_channels_detail_action_restore
        SnapshotDetailAction.FORK -> R.string.save_channels_detail_action_fork
        SnapshotDetailAction.COPY_OVER -> R.string.save_channels_detail_action_copy_over
        SnapshotDetailAction.PIN -> R.string.save_channels_detail_action_pin
        SnapshotDetailAction.UNPIN -> R.string.save_channels_detail_action_unpin
        SnapshotDetailAction.MAKE_SNAPSHOT -> R.string.save_channels_detail_action_make_snapshot
    }
)

@Composable
private fun SnapshotChannelAction.label(): String = stringResource(
    when (this) {
        SnapshotChannelAction.USE_ON_DEVICE -> R.string.save_channels_menu_action_use_on_device
        SnapshotChannelAction.RENAME -> R.string.save_channels_menu_action_rename
        SnapshotChannelAction.SHARE -> R.string.save_channels_menu_action_share
        SnapshotChannelAction.STOP_SHARING -> R.string.save_channels_menu_action_stop_sharing
        SnapshotChannelAction.DELETE -> R.string.save_channels_menu_action_delete
    }
)

@Composable
private fun absoluteTime(iso: String?): String? {
    val context = LocalContext.current
    return remember(iso) {
        iso?.let { raw ->
            val instant = runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(raw) }.getOrNull()
            instant?.let { formatAbsoluteTimestamp(context, it) }
        }
    }
}

private const val AUTO_SLOT = "auto"
