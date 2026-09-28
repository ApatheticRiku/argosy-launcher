package com.nendo.argosy.libretro

import com.nendo.argosy.ui.input.TriggerAxisKeyEmitter

/**
 * Folds every delivery of one key on one device into a single press, and withholds from the core
 * each press that fires a hotkey, plus every other press of that keycode, until the firing press
 * is fully released.
 *
 * A press ends when its last source releases it. A release with no tracked press (it began while
 * a menu was open, or after [clear]) returns [Release.UNTRACKED] and keeps the untracked routing.
 */
class KeyPressGate {

    sealed interface Source {
        data object Key : Source
        data object Touch : Source
        data class Axis(val axis: Int) : Source
    }

    data class Release(val toHotkeys: Boolean, val toCore: Boolean) {
        companion object {
            val UNTRACKED = Release(toHotkeys = true, toCore = true)
            val STILL_HELD = Release(toHotkeys = false, toCore = false)
        }
    }

    private class Press(val offered: Boolean, val withheld: Boolean) {
        val sources = mutableSetOf<Source>()
    }

    private val presses = mutableMapOf<Pair<Int, Int>, Press>()
    private val hotkeyOwners = mutableMapOf<Int, Int>()
    private val withheldAxes = mutableSetOf<Pair<Int, Int>>()

    /**
     * True when this down continues toward the core. [offerHotkey] runs only for the first source
     * of a press whose keycode no hotkey currently holds, and returns true when a hotkey consumed
     * the key. [coreBound] false marks a source whose core input travels the analog path instead.
     */
    fun onDown(
        deviceId: Int,
        keyCode: Int,
        source: Source,
        coreBound: Boolean,
        offerHotkey: () -> Boolean
    ): Boolean {
        val id = deviceId to keyCode
        presses[id]?.let { press ->
            press.sources.add(source)
            return false
        }
        val press = when {
            keyCode in hotkeyOwners -> Press(offered = false, withheld = true)
            offerHotkey() -> {
                hotkeyOwners[keyCode] = deviceId
                Press(offered = true, withheld = true)
            }
            else -> Press(offered = true, withheld = !coreBound)
        }
        press.sources.add(source)
        presses[id] = press
        return !press.withheld
    }

    fun onUp(deviceId: Int, keyCode: Int, source: Source): Release {
        val id = deviceId to keyCode
        val press = presses[id] ?: return Release.UNTRACKED
        press.sources.remove(source)
        if (press.sources.isNotEmpty()) return Release.STILL_HELD
        presses.remove(id)
        if (hotkeyOwners[keyCode] == deviceId) hotkeyOwners.remove(keyCode)
        return Release(toHotkeys = press.offered, toCore = !press.withheld)
    }

    fun isWithheld(deviceId: Int, keyCode: Int): Boolean =
        presses[deviceId to keyCode]?.withheld == true

    /**
     * Whether an analog-mapped axis edge may reach the core. A trigger axis crossing into its press
     * while a hotkey holds that trigger's keycode is refused, and so is its matching release.
     */
    fun admitsAnalog(deviceId: Int, axis: Int, pressed: Boolean): Boolean {
        val id = deviceId to axis
        if (!pressed) return !withheldAxes.remove(id)
        val keyCode = TriggerAxisKeyEmitter.keyCodeForAxis(axis) ?: return true
        if (keyCode !in hotkeyOwners) return true
        withheldAxes.add(id)
        return false
    }

    fun clear() {
        presses.clear()
        hotkeyOwners.clear()
        withheldAxes.clear()
    }
}
