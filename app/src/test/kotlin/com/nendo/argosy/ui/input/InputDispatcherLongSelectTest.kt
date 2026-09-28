package com.nendo.argosy.ui.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherLongSelectTest {

    private val silentHandler = object : InputHandler {}

    private val holdSelect = GamepadInput(GamepadEvent.LongSelect)

    @Test
    fun `held Select is claimed while a critical handler is active`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)
        dispatcher.setCriticalHandler(object : InputHandler {})

        assertTrue(dispatcher.dispatch(holdSelect).handled)
    }

    @Test
    fun `held Select is claimed while a drawer handler is active`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)
        dispatcher.subscribeDrawer(object : InputHandler {})

        assertTrue(dispatcher.dispatch(holdSelect).handled)
    }

    @Test
    fun `held Select is claimed while a modal handler is active`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)
        dispatcher.pushModal(object : InputHandler {})

        assertTrue(dispatcher.dispatch(holdSelect).handled)
    }

    @Test
    fun `held Select left unhandled by the screen reaches the app-level swap`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)

        assertFalse(dispatcher.dispatch(holdSelect).handled)
    }

    @Test
    fun `tap Select left unhandled by a drawer handler is claimed`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)
        dispatcher.subscribeDrawer(object : InputHandler {})

        assertTrue(dispatcher.dispatch(GamepadInput(GamepadEvent.Select)).handled)
    }

    @Test
    fun `tap Select under a background conflict dialog never reaches the swap`() {
        val backgroundConflict = object : InputHandler {
            override fun onUp(): InputResult = InputResult.HANDLED
            override fun onDown(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult = InputResult.HANDLED
            override fun onBack(): InputResult = InputResult.HANDLED
        }
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)
        dispatcher.setCriticalHandler(backgroundConflict)

        assertTrue(dispatcher.dispatch(GamepadInput(GamepadEvent.Select)).handled)
    }

    @Test
    fun `tap Select left unhandled by the screen reaches the app-level swap`() {
        val dispatcher = InputDispatcher()
        dispatcher.subscribeView(silentHandler)

        assertFalse(dispatcher.dispatch(GamepadInput(GamepadEvent.Select)).handled)
    }
}
