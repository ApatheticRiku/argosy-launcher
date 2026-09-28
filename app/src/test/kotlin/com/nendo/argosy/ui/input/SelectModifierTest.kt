package com.nendo.argosy.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectModifierTest {

    private val combos = SelectModifier.comboMapFrom("quick_menu", "quick_settings")

    @Test
    fun `standalone Select is held on press and emits on release`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `Select then L1 emits the combo and swallows the Select release`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertEquals(
            GamepadEvent.LeftStickClick,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
        assertNull(modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_UP))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `Select then R1 emits the right combo`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(
            GamepadEvent.RightStickClick,
            modifier.filter(GamepadEvent.NextSection, KeyEvent.ACTION_DOWN)
        )
    }

    @Test
    fun `an unmapped button while Select is held passes through and keeps Select armed`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(GamepadEvent.Up, modifier.filter(GamepadEvent.Up, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `empty map passes Select through on press and emits nothing on release`() {
        val modifier = SelectModifier(emptyMap())

        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a stick-derived press with no release still emits`() {
        val modifier = SelectModifier(combos)

        assertEquals(GamepadEvent.Left, modifier.filter(GamepadEvent.Left, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Left, modifier.filter(GamepadEvent.Left, KeyEvent.ACTION_DOWN))
    }

    @Test
    fun `L1 without Select is a plain L1`() {
        val modifier = SelectModifier(combos)

        assertEquals(
            GamepadEvent.PrevSection,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
    }

    @Test
    fun `a release that was never pressed is not a Select`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `reset drops a held Select`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        modifier.reset()
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `an armed Select released before the hold is a plain Select`() {
        val modifier = SelectModifier(emptyMap())

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
        assertFalse(modifier.claimHold())
    }

    @Test
    fun `a claimed hold swallows the Select release`() {
        val modifier = SelectModifier(emptyMap())

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true)
        assertTrue(modifier.claimHold())
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `key repeats during a claimed hold do not re-arm the tap`() {
        val modifier = SelectModifier(emptyMap())

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true)
        assertTrue(modifier.claimHold())
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, isRepeat = true))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `an unarmed Select with no combos cannot be claimed as a hold`() {
        val modifier = SelectModifier(emptyMap())

        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertFalse(modifier.claimHold())
    }

    @Test
    fun `a combo fired while Select is held blocks the hold`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true)
        modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        assertFalse(modifier.claimHold())
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a hold claimed first leaves the release swallowed`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true)
        assertTrue(modifier.claimHold())
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a fresh press after a lost release starts a new tap`() {
        val modifier = SelectModifier(emptyMap())

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true)
        assertTrue(modifier.claimHold())
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, holdArmed = true))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a key repeat of a held Select with combos keeps the combo from turning into a tap`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN, isRepeat = true))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a combo set to none leaves its button unmapped`() {
        val modifier = SelectModifier(SelectModifier.comboMapFrom("none", "quick_settings"))

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(
            GamepadEvent.PrevSection,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
    }
}
