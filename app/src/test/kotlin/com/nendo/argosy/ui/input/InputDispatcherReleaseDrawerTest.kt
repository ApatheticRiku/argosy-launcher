package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherReleaseDrawerTest {

    private class Recorder : InputHandler {
        var downs = 0
        override fun onDown(): InputResult {
            downs++
            return InputResult.HANDLED
        }
    }

    private val down = GamepadInput(GamepadEvent.Down)

    @Test
    fun `releasing a handler that is not the drawer leaves the open drawer receiving input`() {
        val dispatcher = InputDispatcher()
        val view = Recorder()
        val panel = Recorder()
        dispatcher.subscribeView(view)
        dispatcher.subscribeDrawer(panel)

        assertFalse(dispatcher.releaseDrawer(Recorder()))
        dispatcher.dispatch(down)

        assertEquals(1, panel.downs)
        assertEquals(0, view.downs)
    }

    @Test
    fun `releasing the drawer handler returns input to the view`() {
        val dispatcher = InputDispatcher()
        val view = Recorder()
        val prompt = Recorder()
        dispatcher.subscribeView(view)
        dispatcher.subscribeDrawer(prompt)

        assertTrue(dispatcher.releaseDrawer(prompt))
        dispatcher.dispatch(down)

        assertEquals(0, prompt.downs)
        assertEquals(1, view.downs)
    }
}
