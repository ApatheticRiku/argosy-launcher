package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType

class BackgroundConflictInputHandler(
    private val moveFocus: (Int) -> Unit,
    private val confirm: () -> Unit,
    private val skip: () -> Unit
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
        confirm()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onBack(): InputResult {
        skip()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }
}
