package com.nendo.argosy.ui.input

/**
 * An [InputHandler] for a modal that owns every button while it is shown. Each method defaults to
 * [InputResult.HANDLED], so a button the modal does not use never reaches the screen or the app-level
 * fallbacks behind it. Implementors override only the buttons they act on.
 */
interface CapturingInputHandler : InputHandler {
    override fun onUp(): InputResult = InputResult.HANDLED
    override fun onDown(): InputResult = InputResult.HANDLED
    override fun onLeft(): InputResult = InputResult.HANDLED
    override fun onRight(): InputResult = InputResult.HANDLED
    override fun onConfirm(): InputResult = InputResult.HANDLED
    override fun onBack(): InputResult = InputResult.HANDLED
    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSecondaryAction(): InputResult = InputResult.HANDLED
    override fun onContextMenu(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
    override fun onSelect(): InputResult = InputResult.HANDLED
    override fun onLeftStickClick(): InputResult = InputResult.HANDLED
    override fun onRightStickClick(): InputResult = InputResult.HANDLED
    override fun onLongConfirm(): InputResult = InputResult.HANDLED
    override fun onLongSelect(): InputResult = InputResult.HANDLED
}
