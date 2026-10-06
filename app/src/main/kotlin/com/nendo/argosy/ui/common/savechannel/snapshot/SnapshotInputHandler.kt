package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.ui.common.savechannel.SaveChannelState
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope

/**
 * Gamepad routing for the snapshot channel view, delegated to by the save modal's host while
 * [SaveChannelState.isSnapshotSavesView] holds. LB/RB are swallowed, the States tab being hidden
 * on snapshot servers. UNHANDLED hands the press back to the host: Back with nothing open closes
 * the modal, and Menu and Select keep the host's modal behavior unless an overlay or label entry
 * is open, where they are swallowed.
 */
class SnapshotInputHandler(
    private val delegate: SnapshotViewDelegate,
    private val saveState: () -> SaveChannelState,
    private val scope: CoroutineScope,
    private val onSaveStatusChanged: (SaveStatusEvent) -> Unit,
    private val onOpenTimeline: () -> Unit
) : InputHandler {

    private val view: SnapshotViewState? get() = saveState().snapshot

    override fun onUp(): InputResult {
        delegate.moveVertical(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        delegate.moveVertical(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        delegate.moveHorizontal(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        delegate.moveHorizontal(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        delegate.confirm(scope, onSaveStatusChanged)
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult =
        if (delegate.back()) InputResult.HANDLED else InputResult.UNHANDLED

    override fun onContextMenu(): InputResult {
        delegate.openChannelActions()
        return InputResult.HANDLED
    }

    override fun onLongConfirm(): InputResult {
        delegate.openChannelActions()
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        if (view?.hasOverlay == false) onOpenTimeline()
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult = overlayGuard()

    override fun onSelect(): InputResult = overlayGuard()

    override fun onPrevSection(): InputResult = sectionSwitch()

    override fun onNextSection(): InputResult = sectionSwitch()

    override fun onPrevTrigger(): InputResult = InputResult.HANDLED

    override fun onNextTrigger(): InputResult = InputResult.HANDLED

    override fun onLeftStickClick(): InputResult = InputResult.HANDLED

    override fun onRightStickClick(): InputResult = InputResult.HANDLED

    private fun sectionSwitch(): InputResult = InputResult.HANDLED

    private fun overlayGuard(): InputResult =
        if (view?.hasOverlay == true) InputResult.HANDLED else InputResult.UNHANDLED
}
