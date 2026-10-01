package com.nendo.argosy.libretro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.DocumentHighlightStore
import com.nendo.argosy.data.repository.GameDocumentLoader
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.gamedetail.GameDocument
import com.nendo.argosy.ui.screens.gamedetail.components.DocumentReaderController
import com.nendo.argosy.ui.screens.gamedetail.components.DocumentReaderOverlay
import com.nendo.argosy.ui.screens.gamedetail.components.gameDocuments
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InGameDocumentKind(val categoryKey: String) {
    MANUAL(VariantCategory.MANUAL.key),
    WALKTHROUGH(VariantCategory.WALKTHROUGH.key)
}

/**
 * The manual and walkthrough of the game in play. Each kind keeps its own reader, so the
 * walkthrough can stay open beside the game while the manual is read from the menu.
 */
class InGameDocuments(
    private val scope: CoroutineScope,
    private val gameRepository: GameRepository,
    private val romMRepository: RomMRepository,
    loader: GameDocumentLoader,
    highlightStore: DocumentHighlightStore
) {
    private val _available = MutableStateFlow<Map<InGameDocumentKind, GameDocument>>(emptyMap())
    val available: StateFlow<Map<InGameDocumentKind, GameDocument>> = _available.asStateFlow()

    private val readers = InGameDocumentKind.entries.associateWith {
        DocumentReaderController(scope, loader, romMRepository, highlightStore)
    }
    private var romId: Long? = null

    fun load(gameId: Long) {
        if (gameId <= 0L) return
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                val game = gameRepository.getById(gameId)
                romId = game?.rommId
                gameDocuments(game, gameRepository.getGameFilesForGame(gameId), romMRepository)
            }
            _available.value = InGameDocumentKind.entries.mapNotNull { kind ->
                found.firstOrNull { it.category == kind.categoryKey }?.let { kind to it }
            }.toMap()
        }
    }

    fun reader(kind: InGameDocumentKind): DocumentReaderController = readers.getValue(kind)

    fun open(kind: InGameDocumentKind) {
        val document = _available.value[kind] ?: return
        val reader = reader(kind)
        if (reader.openDocument == document) return
        reader.open(document, romId)
    }

    fun dismissAll() = readers.values.forEach { it.dismiss() }
}

/**
 * [DocumentReaderController.inputHandler] for the menu panel: Left on the first page is left
 * unhandled so the panel can move focus to its rail.
 */
fun DocumentReaderController.menuPanelInputHandler(onDismiss: () -> Unit): InputHandler {
    val base = inputHandler(onDismiss)
    return object : InputHandler by base {
        override fun onLeft(): InputResult =
            if ((state.value?.pageIndex ?: 0) > 0) base.onLeft() else InputResult.UNHANDLED
    }
}

@Composable
fun InGameDocumentReader(
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
