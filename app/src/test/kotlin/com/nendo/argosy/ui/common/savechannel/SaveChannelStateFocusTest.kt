package com.nendo.argosy.ui.common.savechannel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveChannelStateFocusTest {

    @Test
    fun `a slot counts as focused only in the slots column at its index`() {
        val state = SaveChannelState(saveFocusColumn = SaveFocusColumn.SLOTS, selectedSlotIndex = 2)

        assertTrue(state.isSlotFocused(2))
        assertFalse(state.isSlotFocused(1))
        assertFalse(state.copy(saveFocusColumn = SaveFocusColumn.HISTORY).isSlotFocused(2))
    }

    @Test
    fun `a history entry counts as focused only in the history column at its index`() {
        val state = SaveChannelState(saveFocusColumn = SaveFocusColumn.HISTORY, selectedHistoryIndex = 0)

        assertTrue(state.isHistoryFocused(0))
        assertFalse(state.isHistoryFocused(3))
        assertFalse(state.copy(saveFocusColumn = SaveFocusColumn.SLOTS).isHistoryFocused(0))
    }

    @Test
    fun `a slot and a history entry at the same index are never both focused`() {
        val slots = SaveChannelState(saveFocusColumn = SaveFocusColumn.SLOTS)
        val history = SaveChannelState(saveFocusColumn = SaveFocusColumn.HISTORY)

        assertTrue(slots.isSlotFocused(0) && !slots.isHistoryFocused(0))
        assertTrue(history.isHistoryFocused(0) && !history.isSlotFocused(0))
    }

    @Test
    fun `a state counts as focused only on the states tab`() {
        val state = SaveChannelState(selectedTab = SaveTab.STATES, focusIndex = 1)

        assertTrue(state.isStateFocused(1))
        assertFalse(state.isStateFocused(0))
        assertFalse(state.copy(selectedTab = SaveTab.SAVES).isStateFocused(1))
    }

    @Test
    fun `a slot picker item counts as focused only while the picker is open`() {
        val state = SaveChannelState(showSlotPicker = true, slotPickerIndex = 1)

        assertTrue(state.isSlotPickerItemFocused(1))
        assertFalse(state.isSlotPickerItemFocused(0))
        assertFalse(state.copy(showSlotPicker = false).isSlotPickerItemFocused(1))
    }
}
