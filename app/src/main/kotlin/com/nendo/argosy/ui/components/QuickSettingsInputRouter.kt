package com.nendo.argosy.ui.components

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Sends each button to the handler in [pageHandlers] for the page [activePage] reports and falls
 * back to [panelHandler] for anything that page leaves unhandled, such as page switching and
 * closing the panel. Pages without an entry go straight to [panelHandler].
 */
class QuickSettingsInputRouter(
    private val panelHandler: InputHandler,
    private val pageHandlers: Map<QuickSettingsPage, InputHandler>,
    private val activePage: () -> QuickSettingsPage
) : InputHandler {

    private inline fun route(press: (InputHandler) -> InputResult): InputResult {
        val pageHandler = pageHandlers[activePage()]
        if (pageHandler != null) {
            val result = press(pageHandler)
            if (result.handled) return result
        }
        return press(panelHandler)
    }

    override fun onUp() = route { it.onUp() }
    override fun onDown() = route { it.onDown() }
    override fun onLeft() = route { it.onLeft() }
    override fun onRight() = route { it.onRight() }
    override fun onConfirm() = route { it.onConfirm() }
    override fun onBack() = route { it.onBack() }
    override fun onMenu() = route { it.onMenu() }
    override fun onSecondaryAction() = route { it.onSecondaryAction() }
    override fun onContextMenu() = route { it.onContextMenu() }
    override fun onPrevSection() = route { it.onPrevSection() }
    override fun onNextSection() = route { it.onNextSection() }
    override fun onPrevTrigger() = route { it.onPrevTrigger() }
    override fun onNextTrigger() = route { it.onNextTrigger() }
    override fun onSelect() = route { it.onSelect() }
    override fun onLeftStickClick() = route { it.onLeftStickClick() }
    override fun onRightStickClick() = route { it.onRightStickClick() }
    override fun onLongConfirm() = route { it.onLongConfirm() }
    override fun onLongSelect() = route { it.onLongSelect() }
}
