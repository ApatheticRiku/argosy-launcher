package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherRouteGateTest {

    private class Recorder : InputHandler {
        var confirms = 0
        override fun onConfirm(): InputResult {
            confirms++
            return InputResult.HANDLED
        }
    }

    private val confirm = GamepadInput(GamepadEvent.Confirm)

    @Test
    fun `a screen that re-subscribes while the route lags never keeps input on the next screen`() {
        val dispatcher = InputDispatcher()
        val home = Recorder()
        val settings = Recorder()
        dispatcher.setCurrentRoute("home")
        dispatcher.subscribeView(home, forRoute = "home")

        dispatcher.subscribeView(settings, forRoute = "settings")
        dispatcher.subscribeView(home, forRoute = "home")
        dispatcher.setCurrentRoute("settings")
        dispatcher.dispatch(confirm)

        assertEquals(0, home.confirms)
        assertEquals(1, settings.confirms)
    }

    @Test
    fun `input on a route whose screen has not subscribed reaches no screen`() {
        val dispatcher = InputDispatcher()
        val home = Recorder()
        dispatcher.setCurrentRoute("home")
        dispatcher.subscribeView(home, forRoute = "home")

        dispatcher.setCurrentRoute("settings")
        val result = dispatcher.dispatch(confirm)

        assertEquals(0, home.confirms)
        assertTrue(result.handled)
    }

    @Test
    fun `the subscribed screen still receives input on its own route with arguments`() {
        val dispatcher = InputDispatcher()
        val detail = Recorder()
        dispatcher.setCurrentRoute("game_detail/42")
        dispatcher.subscribeView(detail, forRoute = "game_detail")

        dispatcher.dispatch(confirm)

        assertEquals(1, detail.confirms)
    }

    @Test
    fun `a view subscribed without a route receives input on any route`() {
        val dispatcher = InputDispatcher()
        val firstRun = Recorder()
        dispatcher.subscribeView(firstRun)
        dispatcher.setCurrentRoute("first_run")

        dispatcher.dispatch(confirm)

        assertEquals(1, firstRun.confirms)
    }

    @Test
    fun `a modal shown through its host handler counts as a capturing overlay`() {
        val dispatcher = InputDispatcher()
        val token = Any()

        dispatcher.markModalShown(token)

        assertTrue(dispatcher.hasCapturingOverlay())
    }
}
