package com.nendo.argosy.libretro

import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPressGateTest {

    private val r2 = KeyEvent.KEYCODE_BUTTON_R2
    private val pad = 7
    private val otherPad = 8
    private val touchDevice = -1
    private val key = KeyPressGate.Source.Key
    private val rTrigger = KeyPressGate.Source.Axis(MotionEvent.AXIS_RTRIGGER)
    private val gas = KeyPressGate.Source.Axis(MotionEvent.AXIS_GAS)

    private class Pipeline(boundKeyCode: Int?) {
        val gate = KeyPressGate()
        val hotkeys = HotkeyManager(mockk<InputConfigRepository>(relaxed = true)).apply {
            setHotkeys(
                listOfNotNull(
                    boundKeyCode?.let {
                        HotkeyEntity(action = HotkeyAction.FAST_FORWARD, buttonComboJson = "[$it]")
                    }
                )
            )
        }
        val core = mutableListOf<String>()
        var fastForwardFired = 0

        fun down(deviceId: Int, keyCode: Int, source: KeyPressGate.Source, coreBound: Boolean = true) {
            val forward = gate.onDown(deviceId, keyCode, source, coreBound) {
                val fired = hotkeys.onKeyDown(keyCode, "pad")?.action == HotkeyAction.FAST_FORWARD
                if (fired) fastForwardFired++
                fired
            }
            if (forward) core.add("down:$keyCode")
        }

        fun up(deviceId: Int, keyCode: Int, source: KeyPressGate.Source) {
            val release = gate.onUp(deviceId, keyCode, source)
            if (release.toHotkeys) hotkeys.onKeyUp(keyCode)
            if (release.toCore) core.add("up:$keyCode")
        }

        fun analog(deviceId: Int, axis: Int, pressed: Boolean) {
            if (gate.admitsAnalog(deviceId, axis, pressed)) core.add(if (pressed) "analog-down" else "analog-up")
        }

        val fastForwardHeld: Boolean get() = hotkeys.isHotkeyActive(HotkeyAction.FAST_FORWARD)
    }

    @Test
    fun `key and axis delivery of a bound trigger fires once and never reaches the core`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.down(pad, r2, rTrigger)
        pipeline.down(pad, r2, gas)

        assertEquals(1, pipeline.fastForwardFired)
        assertTrue(pipeline.fastForwardHeld)

        pipeline.up(pad, r2, key)
        pipeline.up(pad, r2, rTrigger)
        pipeline.up(pad, r2, gas)

        assertEquals(emptyList<String>(), pipeline.core)
        assertFalse(pipeline.fastForwardHeld)
    }

    @Test
    fun `key released before axis keeps the hotkey held and leaks nothing`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.down(pad, r2, rTrigger)
        pipeline.up(pad, r2, key)

        assertTrue(pipeline.fastForwardHeld)
        assertTrue(pipeline.gate.isWithheld(pad, r2))

        pipeline.up(pad, r2, rTrigger)

        assertFalse(pipeline.fastForwardHeld)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `axis released before key keeps the hotkey held and leaks nothing`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, rTrigger)
        pipeline.down(pad, r2, key)
        pipeline.up(pad, r2, rTrigger)

        assertTrue(pipeline.fastForwardHeld)

        pipeline.up(pad, r2, key)

        assertEquals(1, pipeline.fastForwardFired)
        assertFalse(pipeline.fastForwardHeld)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `a second press after full release fires again`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.down(pad, r2, rTrigger)
        pipeline.up(pad, r2, rTrigger)
        pipeline.up(pad, r2, key)
        pipeline.down(pad, r2, rTrigger)
        pipeline.down(pad, r2, key)

        assertEquals(2, pipeline.fastForwardFired)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `an unbound trigger reaches the core as one press whichever source releases last`() {
        val keyFirst = Pipeline(boundKeyCode = KeyEvent.KEYCODE_BUTTON_L2)
        keyFirst.down(pad, r2, key)
        keyFirst.down(pad, r2, rTrigger)
        keyFirst.up(pad, r2, key)
        assertEquals(listOf("down:$r2"), keyFirst.core)
        keyFirst.up(pad, r2, rTrigger)
        assertEquals(listOf("down:$r2", "up:$r2"), keyFirst.core)

        val axisFirst = Pipeline(boundKeyCode = null)
        axisFirst.down(pad, r2, rTrigger)
        axisFirst.down(pad, r2, key)
        axisFirst.up(pad, r2, rTrigger)
        axisFirst.up(pad, r2, key)
        assertEquals(listOf("down:$r2", "up:$r2"), axisFirst.core)
        assertEquals(0, axisFirst.fastForwardFired)
    }

    @Test
    fun `touch and another pad pressing a held hotkey keycode stay out of the core`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.down(touchDevice, r2, KeyPressGate.Source.Touch)
        pipeline.down(otherPad, r2, key)
        pipeline.up(touchDevice, r2, KeyPressGate.Source.Touch)
        pipeline.up(otherPad, r2, key)

        assertTrue(pipeline.fastForwardHeld)

        pipeline.up(pad, r2, key)

        assertEquals(1, pipeline.fastForwardFired)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `an analog mapped trigger bound to a hotkey never reaches the core`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, rTrigger, coreBound = false)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = true)
        pipeline.up(pad, r2, rTrigger)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = false)

        assertEquals(1, pipeline.fastForwardFired)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `an analog mapped trigger held by a key hotkey is refused on either release order`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.down(pad, r2, rTrigger, coreBound = false)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = true)
        pipeline.up(pad, r2, key)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = false)
        pipeline.up(pad, r2, rTrigger)

        assertEquals(1, pipeline.fastForwardFired)
        assertEquals(emptyList<String>(), pipeline.core)
    }

    @Test
    fun `an analog mapped trigger with no hotkey reaches the core only through the analog path`() {
        val pipeline = Pipeline(boundKeyCode = null)

        pipeline.down(pad, r2, rTrigger, coreBound = false)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = true)
        pipeline.up(pad, r2, rTrigger)
        pipeline.analog(pad, MotionEvent.AXIS_RTRIGGER, pressed = false)

        assertEquals(listOf("analog-down", "analog-up"), pipeline.core)
    }

    @Test
    fun `analog edges on axes that are not triggers are always admitted`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.analog(pad, MotionEvent.AXIS_Z, pressed = true)
        pipeline.analog(pad, MotionEvent.AXIS_Z, pressed = false)

        assertEquals(listOf("analog-down", "analog-up"), pipeline.core)
    }

    @Test
    fun `a release with no tracked press keeps the untracked routing`() {
        val pipeline = Pipeline(boundKeyCode = r2)

        pipeline.down(pad, r2, key)
        pipeline.gate.clear()
        pipeline.up(pad, r2, rTrigger)

        assertEquals(listOf("up:$r2"), pipeline.core)
    }
}
