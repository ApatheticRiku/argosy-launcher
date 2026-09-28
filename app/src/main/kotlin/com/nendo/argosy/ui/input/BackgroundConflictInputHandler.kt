package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.sync.ConflictResolution

class BackgroundConflictInputHandler(
    private val moveFocus: (Int) -> Unit,
    private val focusedButton: () -> Int,
    private val resolve: (ConflictResolution) -> Unit
) : CapturingInputHandler {

    override fun onUp(): InputResult {
        moveFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        moveFocus(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        when (focusedButton()) {
            0 -> resolve(ConflictResolution.KEEP_LOCAL)
            1 -> resolve(ConflictResolution.KEEP_SERVER)
            2 -> resolve(ConflictResolution.SKIP)
        }
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onBack(): InputResult {
        resolve(ConflictResolution.SKIP)
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }
}
