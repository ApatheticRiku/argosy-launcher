package com.nendo.argosy.ui.input

import com.nendo.argosy.data.sync.ConflictResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundConflictInputHandlerTest {

    private val resolved = mutableListOf<ConflictResolution>()
    private val moves = mutableListOf<Int>()

    private val dialog = BackgroundConflictInputHandler(
        moveFocus = { moves += it },
        focusedButton = { 1 },
        resolve = { resolved += it }
    )

    private fun dispatcherUnderDialog(): InputDispatcher =
        InputDispatcher().apply {
            subscribeView(object : InputHandler {})
            setCriticalHandler(dialog)
        }

    @Test
    fun `Menu under the dialog never reaches the drawer fallback`() {
        val result = dispatcherUnderDialog().dispatch(GamepadInput(GamepadEvent.Menu))

        assertTrue(result.handled)
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `Left under the dialog never reaches the drawer fallback`() {
        val result = dispatcherUnderDialog().dispatch(GamepadInput(GamepadEvent.Left))

        assertTrue(result.handled)
        assertTrue(moves.isEmpty())
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `every button the dialog does not use is swallowed`() {
        val dispatcher = dispatcherUnderDialog()
        val unused = listOf(
            GamepadEvent.Right,
            GamepadEvent.ContextMenu,
            GamepadEvent.SecondaryAction,
            GamepadEvent.PrevSection,
            GamepadEvent.NextSection,
            GamepadEvent.PrevTrigger,
            GamepadEvent.NextTrigger,
            GamepadEvent.LeftStickClick,
            GamepadEvent.RightStickClick,
            GamepadEvent.Select,
            GamepadEvent.LongConfirm
        )

        unused.forEach { event ->
            assertTrue(event.toString(), dispatcher.dispatch(GamepadInput(event)).handled)
        }
        assertTrue(moves.isEmpty())
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `the dialog's own buttons keep their actions`() {
        val dispatcher = dispatcherUnderDialog()

        dispatcher.dispatch(GamepadInput(GamepadEvent.Down))
        dispatcher.dispatch(GamepadInput(GamepadEvent.Confirm))
        dispatcher.dispatch(GamepadInput(GamepadEvent.Back))

        assertEquals(listOf(1), moves)
        assertEquals(listOf(ConflictResolution.KEEP_SERVER, ConflictResolution.SKIP), resolved)
    }
}
