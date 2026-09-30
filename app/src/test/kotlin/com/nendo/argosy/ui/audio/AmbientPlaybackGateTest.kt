package com.nendo.argosy.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientPlaybackGateTest {

    private fun openGate() = AmbientPlaybackGate().apply { enabled = true }

    @Test
    fun `an enabled gate with no holds lets fade-in through`() {
        assertNull(openGate().fadeInBlock())
    }

    @Test
    fun `a user pause blocks fade-in`() {
        val gate = openGate().apply { userPaused = true }
        assertEquals(FadeInBlock.USER_PAUSED, gate.fadeInBlock())
    }

    @Test
    fun `clearing the user pause lets fade-in through again`() {
        val gate = openGate().apply { userPaused = true }
        gate.userPaused = false
        assertNull(gate.fadeInBlock())
    }

    @Test
    fun `releasing a silence hold does not override a user pause`() {
        val gate = openGate().apply {
            userPaused = true
            silenceHolds = 1
        }
        gate.silenceHolds = 0
        assertEquals(FadeInBlock.USER_PAUSED, gate.fadeInBlock())
    }

    @Test
    fun `resuming from suspend does not override a user pause`() {
        val gate = openGate().apply {
            userPaused = true
            suspended = true
        }
        gate.suspended = false
        assertEquals(FadeInBlock.USER_PAUSED, gate.fadeInBlock())
    }

    @Test
    fun `a disabled gate blocks fade-in`() {
        assertEquals(FadeInBlock.DISABLED, AmbientPlaybackGate().fadeInBlock())
    }

    @Test
    fun `silence holds and suspend each block fade-in`() {
        assertEquals(FadeInBlock.SILENCE_HELD, openGate().apply { silenceHolds = 2 }.fadeInBlock())
        assertEquals(FadeInBlock.SUSPENDED, openGate().apply { suspended = true }.fadeInBlock())
    }
}
