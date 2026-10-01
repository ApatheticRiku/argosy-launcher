package com.nendo.argosy.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Marks a modal as on screen for as long as it is composed, so app-wide shortcuts stay closed
 * while it is open whichever handler routes its input.
 */
@Composable
fun ModalPresenceEffect() {
    val inputDispatcher = LocalModalPresence.current ?: return
    DisposableEffect(inputDispatcher) {
        val token = Any()
        inputDispatcher.markModalShown(token)
        onDispose { inputDispatcher.markModalHidden(token) }
    }
}

val LocalModalPresence = staticCompositionLocalOf<InputDispatcher?> { null }

/** Captures gamepad input for a modal while [active] by pushing [handler] on the modal stack. */
@Composable
fun ModalInputEffect(active: Boolean, handler: InputHandler) {
    val inputDispatcher = LocalInputDispatcher.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, handler, active) {
        if (!active) return@DisposableEffect onDispose { }
        inputDispatcher.pushModal(handler)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(handler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(handler)
        }
    }
}
