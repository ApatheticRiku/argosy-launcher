package com.nendo.argosy.ui.input

import com.nendo.argosy.data.netplay.NetplayJoinService
import com.nendo.argosy.data.netplay.NetplayJoinState
import com.nendo.argosy.data.netplay.VerifySubState

class NetplayJoinInputHandler(
    private val service: NetplayJoinService,
    private val onDismiss: () -> Unit
) : CapturingInputHandler {

    override fun onUp(): InputResult {
        move(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        move(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val state = service.state.value as? NetplayJoinState.VerifyingGame
        when (state?.sub) {
            is VerifySubState.AmbiguousCandidates, is VerifySubState.HashMismatchVariants ->
                service.confirmFocused()
            else -> Unit
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        onDismiss()
        return InputResult.HANDLED
    }

    private fun move(delta: Int) {
        val state = service.state.value as? NetplayJoinState.VerifyingGame ?: return
        when (state.sub) {
            is VerifySubState.AmbiguousCandidates -> service.moveCandidateFocus(delta)
            is VerifySubState.HashMismatchVariants -> service.moveVariantFocus(delta)
            else -> Unit
        }
    }
}
