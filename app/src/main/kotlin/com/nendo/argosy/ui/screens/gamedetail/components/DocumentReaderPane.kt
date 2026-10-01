package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * The document [reader] has open, drawn by [DocumentReaderOverlay]; nothing while it is closed.
 * Shared by the in-game menu, the in-game side panel and the touch-only companion dashboard;
 * [showsControllerHints] is false wherever the pad does not drive the reader.
 */
@Composable
fun DocumentReaderPane(
    reader: DocumentReaderController,
    showsControllerHints: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by reader.state.collectAsState()
    val current = state ?: return
    Box(modifier = modifier) {
        DocumentReaderOverlay(
            state = current,
            onLinesPerPageMeasured = reader::setLinesPerPage,
            onDismiss = onDismiss,
            onTurnPage = reader::turnPage,
            onSpreadsMeasured = reader::setShowsSpreads,
            onToggleHighlight = reader::toggleHighlightAt,
            onCycleHighlightColor = reader::cycleHighlightColor,
            showsControllerHints = showsControllerHints
        )
    }
}
