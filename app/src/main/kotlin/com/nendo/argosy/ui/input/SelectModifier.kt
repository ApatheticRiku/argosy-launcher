package com.nendo.argosy.ui.input

import android.view.KeyEvent

class SelectModifier(comboMap: Map<GamepadEvent, GamepadEvent> = emptyMap()) {

    private enum class State { IDLE, HELD, COMBO_FIRED, HOLD_FIRED }

    private var state = State.IDLE
    private var selectDeferred = false

    var comboMap: Map<GamepadEvent, GamepadEvent> = comboMap

    fun reset() {
        state = State.IDLE
    }

    /**
     * Returns the event to emit now for [event] arriving with [action], or null when this key
     * action emits nothing. [holdArmed] is read only on the first press of Select and decides for
     * the whole press whether a hold can claim it.
     */
    fun filter(
        event: GamepadEvent,
        action: Int,
        isRepeat: Boolean = false,
        holdArmed: Boolean = false
    ): GamepadEvent? {
        if (event == GamepadEvent.Select) return filterSelect(action, isRepeat, holdArmed)
        if (action != KeyEvent.ACTION_DOWN) return null
        if (state == State.IDLE) return event
        val comboEvent = comboMap[event] ?: return event
        state = State.COMBO_FIRED
        return comboEvent
    }

    /**
     * Turns a Select still held with nothing fired into a hold. True when the hold claimed it.
     */
    fun claimHold(): Boolean {
        if (state != State.HELD) return false
        state = State.HOLD_FIRED
        return true
    }

    private fun filterSelect(action: Int, isRepeat: Boolean, holdArmed: Boolean): GamepadEvent? =
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (!isRepeat) {
                    selectDeferred = comboMap.isNotEmpty() || holdArmed
                    state = if (selectDeferred) State.HELD else State.IDLE
                }
                if (selectDeferred) null else GamepadEvent.Select
            }
            KeyEvent.ACTION_UP -> {
                val wasHeld = state == State.HELD
                if (selectDeferred) state = State.IDLE
                if (selectDeferred && wasHeld) GamepadEvent.Select else null
            }
            else -> null
        }

    companion object {
        fun comboMapFrom(selectLCombo: String, selectRCombo: String): Map<GamepadEvent, GamepadEvent> {
            val map = mutableMapOf<GamepadEvent, GamepadEvent>()
            comboActionToEvent(selectLCombo)?.let { map[GamepadEvent.PrevSection] = it }
            comboActionToEvent(selectRCombo)?.let { map[GamepadEvent.NextSection] = it }
            return map
        }

        private fun comboActionToEvent(action: String): GamepadEvent? = when (action) {
            "quick_menu" -> GamepadEvent.LeftStickClick
            "quick_settings" -> GamepadEvent.RightStickClick
            else -> null
        }
    }
}
