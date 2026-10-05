package com.nendo.argosy.ui.screens.savetimeline

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Gamepad routing for the save timeline. While the detail sheet, the channel menu or one of
 * their prompts is open, every press goes to that layer and nothing reaches the lanes.
 */
class SaveTimelineInputHandler(
    private val viewModel: SaveTimelineViewModel,
    private val onBack: () -> Unit
) : InputHandler {

    private val overlayOpen: Boolean get() = viewModel.isOverlayOpen

    override fun onUp(): InputResult = vertical(-1)

    override fun onDown(): InputResult = vertical(1)

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    override fun onPrevSection(): InputResult = jump(-1)

    override fun onNextSection(): InputResult = jump(1)

    override fun onConfirm(): InputResult {
        if (overlayOpen) viewModel.overlayConfirm() else viewModel.openFocusedDetail()
        return InputResult.HANDLED
    }

    override fun onLongConfirm(): InputResult {
        if (overlayOpen) return InputResult.handled(SoundType.SILENT)
        viewModel.openFocusedChannelActions()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        if (!overlayOpen) viewModel.openFocusedChannelActions()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (overlayOpen) {
            viewModel.overlayBack()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        viewModel.exit(onBack)
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        if (!overlayOpen) viewModel.exit(onBack)
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult = if (overlayOpen) InputResult.HANDLED else InputResult.UNHANDLED

    override fun onSelect(): InputResult = if (overlayOpen) InputResult.HANDLED else InputResult.UNHANDLED

    override fun onPrevTrigger(): InputResult = InputResult.HANDLED

    override fun onNextTrigger(): InputResult = InputResult.HANDLED

    override fun onLeftStickClick(): InputResult = InputResult.HANDLED

    override fun onRightStickClick(): InputResult = InputResult.HANDLED

    private fun vertical(delta: Int): InputResult {
        if (overlayOpen) {
            viewModel.overlayVertical(delta)
            return InputResult.HANDLED
        }
        return moved(viewModel.moveLane(delta))
    }

    private fun horizontal(delta: Int): InputResult {
        if (overlayOpen) {
            viewModel.overlayHorizontal(delta)
            return InputResult.HANDLED
        }
        return moved(viewModel.moveAlong(delta))
    }

    private fun jump(direction: Int): InputResult {
        if (overlayOpen) return InputResult.HANDLED
        return moved(viewModel.jump(direction))
    }

    private fun moved(didMove: Boolean): InputResult =
        if (didMove) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
}
