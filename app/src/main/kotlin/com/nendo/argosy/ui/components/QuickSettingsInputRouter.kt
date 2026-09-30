package com.nendo.argosy.ui.components

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Sends each button to [pageHandler] while [pageActive] holds and falls back to [panelHandler]
 * for anything the page leaves unhandled, such as page switching and closing the panel.
 */
class QuickSettingsInputRouter(
    private val panelHandler: InputHandler,
    private val pageHandler: InputHandler,
    private val pageActive: () -> Boolean
) : InputHandler {

    private inline fun route(page: () -> InputResult, panel: () -> InputResult): InputResult {
        if (pageActive()) {
            val result = page()
            if (result.handled) return result
        }
        return panel()
    }

    override fun onUp() = route({ pageHandler.onUp() }, { panelHandler.onUp() })
    override fun onDown() = route({ pageHandler.onDown() }, { panelHandler.onDown() })
    override fun onLeft() = route({ pageHandler.onLeft() }, { panelHandler.onLeft() })
    override fun onRight() = route({ pageHandler.onRight() }, { panelHandler.onRight() })
    override fun onConfirm() = route({ pageHandler.onConfirm() }, { panelHandler.onConfirm() })
    override fun onBack() = route({ pageHandler.onBack() }, { panelHandler.onBack() })
    override fun onMenu() = route({ pageHandler.onMenu() }, { panelHandler.onMenu() })
    override fun onSecondaryAction() =
        route({ pageHandler.onSecondaryAction() }, { panelHandler.onSecondaryAction() })
    override fun onContextMenu() = route({ pageHandler.onContextMenu() }, { panelHandler.onContextMenu() })
    override fun onPrevSection() = route({ pageHandler.onPrevSection() }, { panelHandler.onPrevSection() })
    override fun onNextSection() = route({ pageHandler.onNextSection() }, { panelHandler.onNextSection() })
    override fun onPrevTrigger() = route({ pageHandler.onPrevTrigger() }, { panelHandler.onPrevTrigger() })
    override fun onNextTrigger() = route({ pageHandler.onNextTrigger() }, { panelHandler.onNextTrigger() })
    override fun onSelect() = route({ pageHandler.onSelect() }, { panelHandler.onSelect() })
    override fun onLeftStickClick() =
        route({ pageHandler.onLeftStickClick() }, { panelHandler.onLeftStickClick() })
    override fun onRightStickClick() =
        route({ pageHandler.onRightStickClick() }, { panelHandler.onRightStickClick() })
    override fun onLongConfirm() = route({ pageHandler.onLongConfirm() }, { panelHandler.onLongConfirm() })
    override fun onLongSelect() = route({ pageHandler.onLongSelect() }, { panelHandler.onLongSelect() })
}
