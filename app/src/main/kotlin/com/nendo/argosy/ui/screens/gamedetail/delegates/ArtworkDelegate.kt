package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.input.SoundFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArtworkRow {
    val slot: ArtSlot

    data class Pick(override val slot: ArtSlot) : ArtworkRow

    data class Revert(override val slot: ArtSlot) : ArtworkRow
}

/**
 * The Artwork menu rows in render order: one pick row per slot, then a revert row for each slot
 * that carries an override.
 */
fun artworkRows(overriddenSlots: Set<ArtSlot>): List<ArtworkRow> =
    ArtSlot.entries.map { ArtworkRow.Pick(it) } +
        ArtSlot.entries.filter { it in overriddenSlots }.map { ArtworkRow.Revert(it) }

data class ArtworkMenuState(
    val showArtworkMenu: Boolean = false,
    val artworkFocusIndex: Int = 0
)

class ArtworkDelegate @Inject constructor(
    private val imageCacheManager: ImageCacheManager,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(ArtworkMenuState())
    val state: StateFlow<ArtworkMenuState> = _state.asStateFlow()

    fun reset() {
        _state.value = ArtworkMenuState()
    }

    fun showArtworkMenu() {
        _state.update { it.copy(showArtworkMenu = true, artworkFocusIndex = 0) }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissArtworkMenu() {
        _state.update { it.copy(showArtworkMenu = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun leaveForPicker() {
        _state.update { it.copy(showArtworkMenu = false) }
    }

    fun returnFromPicker(rowCount: Int) {
        _state.update {
            it.copy(
                showArtworkMenu = true,
                artworkFocusIndex = it.artworkFocusIndex.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
            )
        }
    }

    fun moveFocus(delta: Int, rowCount: Int) {
        if (rowCount <= 0) return
        _state.update { it.copy(artworkFocusIndex = (it.artworkFocusIndex + delta).mod(rowCount)) }
    }

    fun setFocus(index: Int, rowCount: Int) {
        if (rowCount <= 0) return
        _state.update { it.copy(artworkFocusIndex = index.coerceIn(0, rowCount - 1)) }
    }

    fun clampFocus(rowCount: Int) {
        _state.update {
            it.copy(artworkFocusIndex = it.artworkFocusIndex.coerceIn(0, (rowCount - 1).coerceAtLeast(0)))
        }
    }

    fun focusedRow(overriddenSlots: Set<ArtSlot>): ArtworkRow? =
        artworkRows(overriddenSlots).getOrNull(_state.value.artworkFocusIndex)

    /**
     * Stores [source] as [slot]'s override. [source] is an absolute local path or a remote url.
     * A failure raises an error notice; [onFinished] runs either way.
     */
    fun applyOverride(
        scope: CoroutineScope,
        gameId: Long,
        slot: ArtSlot,
        source: String,
        onFinished: () -> Unit
    ) {
        scope.launch {
            val applied = if (source.startsWith("/")) {
                imageCacheManager.applyArtOverrideFromFile(gameId, slot, source)
            } else {
                imageCacheManager.applyArtOverride(gameId, slot, source)
            }
            if (!applied) {
                notificationManager.showError(NotificationText.Res(R.string.gamedetail_notice_artwork_failed))
            }
            onFinished()
        }
    }

    fun revertOverride(scope: CoroutineScope, gameId: Long, slot: ArtSlot, onFinished: () -> Unit) {
        scope.launch {
            imageCacheManager.clearArtOverride(gameId, slot)
            onFinished()
        }
    }
}
