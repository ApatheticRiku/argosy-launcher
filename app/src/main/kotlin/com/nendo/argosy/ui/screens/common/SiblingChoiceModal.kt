package com.nendo.argosy.ui.screens.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.ui.common.detailTokens
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens

private const val TOKEN_SEPARATOR = ", "

@Composable
fun SiblingChoiceModalHost(
    state: SiblingChoiceState?,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                currentOnMove(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                currentOnMove(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.SILENT)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = state != null, handler = inputHandler)

    val content = state ?: return
    val isDownload = content.purpose == SiblingChoicePurpose.DOWNLOAD
    Modal(
        title = if (isDownload) {
            stringResource(R.string.ui_sibling_choice_title_download)
        } else {
            stringResource(R.string.ui_sibling_choice_title_active)
        },
        onDismiss = onDismiss
    ) {
        when {
            content.isLoading -> SiblingChoiceMessage(stringResource(R.string.ui_sibling_choice_loading))
            content.loadFailed -> SiblingChoiceMessage(stringResource(R.string.ui_sibling_choice_load_failed))
            content.rowCount == 0 -> SiblingChoiceMessage(stringResource(R.string.ui_sibling_choice_empty))
            else -> SiblingChoiceList(content, onFocus, onConfirm)
        }
    }
}

@Composable
private fun SiblingChoiceMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Dimens.spacingSm)
    )
}

@Composable
private fun ColumnScope.SiblingChoiceList(
    content: SiblingChoiceState,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = content.focusIndex)
    val isDownload = content.purpose == SiblingChoicePurpose.DOWNLOAD
    val group = content.group
    val shownLabel = group?.shownMember?.let { memberSubtext(it) ?: it.title }

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false)
    ) {
        if (content.hasAutomaticRow) {
            item(key = "automatic") {
                OptionItem(
                    icon = Icons.Default.Autorenew,
                    label = stringResource(R.string.ui_sibling_choice_automatic),
                    subtext = shownLabel?.let {
                        stringResource(R.string.ui_sibling_choice_automatic_showing, it)
                    },
                    isFocused = content.focusIndex == 0,
                    isSelected = group?.hasPick == false,
                    onClick = {
                        onFocus(0)
                        onConfirm()
                    }
                )
            }
        }
        itemsIndexed(content.members, key = { _, member -> member.gameId }) { index, member ->
            val rowIndex = content.rowIndexOf(index)
            val downloadIcon = when {
                member.isDownloaded -> Icons.Default.DownloadDone
                isDownload -> Icons.Default.Download
                else -> Icons.Default.CloudOff
            }
            OptionItem(
                icon = downloadIcon,
                iconTint = if (!member.isDownloaded && isDownload) MaterialTheme.colorScheme.primary else null,
                label = member.title,
                subtext = memberSubtext(member),
                isFocused = content.focusIndex == rowIndex,
                isSelected = if (isDownload) member.isShown else member.isPicked,
                onClick = {
                    onFocus(rowIndex)
                    onConfirm()
                }
            )
        }
    }
}

@Composable
private fun memberSubtext(member: SiblingGroupMember): String? {
    val kindLabel = member.kind.labelRes?.let { stringResource(it) }
    val tokens = remember(member.fileName, member.regions) { member.detailTokens }
    return (listOfNotNull(kindLabel) + tokens)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(TOKEN_SEPARATOR)
}
