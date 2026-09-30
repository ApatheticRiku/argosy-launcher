package com.nendo.argosy.ui.components.musicplayer

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class MusicPlayerInputHandler(
    private val viewModel: MusicPlayerViewModel,
    private val onOpenRommSignIn: () -> Unit
) : InputHandler {

    private val browseOpen: Boolean get() = viewModel.uiState.value.browse != null

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val moved = if (browseOpen) viewModel.moveBrowseFocus(delta) else viewModel.moveRow(delta)
        return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(delta: Int): InputResult {
        if (browseOpen) return InputResult.handled(SoundType.SILENT)
        return when (viewModel.uiState.value.focusedRow) {
            MusicPlayerRow.TRANSPORT -> {
                viewModel.moveTransport(delta)
                InputResult.HANDLED
            }
            MusicPlayerRow.LAUNCHER_TOGGLE -> {
                val enabled = viewModel.setLauncherMusic(delta > 0)
                InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
            }
            MusicPlayerRow.PLAYLISTS,
            MusicPlayerRow.SOUNDTRACKS -> InputResult.handled(SoundType.SILENT)
        }
    }

    override fun onConfirm(): InputResult {
        if (browseOpen) {
            viewModel.confirmBrowse()
            return InputResult.HANDLED
        }
        if (viewModel.uiState.value.focusedRow == MusicPlayerRow.LAUNCHER_TOGGLE) {
            val enabled = viewModel.toggleLauncherMusic()
            return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
        }
        viewModel.confirmRow()
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        if (!viewModel.needsRommSignIn) return InputResult.UNHANDLED
        onOpenRommSignIn()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (!browseOpen) return InputResult.UNHANDLED
        viewModel.closeBrowse()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }
}
