package com.nendo.argosy.ui.components.friends

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.input.toggleLeftRight

class QuickFriendsInputHandler(
    private val controller: QuickFriendsController,
    private val showQuayPass: () -> Boolean,
    private val quayPassEnabled: () -> Boolean,
    private val wrapMode: () -> MenuWrapMode,
    private val onToggleQuayPass: () -> Unit,
    private val onOpenProfile: (Friend) -> Unit
) : InputHandler {

    private fun rows(): List<QuickFriendsRow> = quickFriendsRows(showQuayPass(), controller.state.value)

    private fun focusedRow(): QuickFriendsRow? = rows().getOrNull(controller.state.value.focusIndex)

    private fun move(delta: Int): InputResult {
        val rows = rows()
        if (rows.isEmpty()) return InputResult.UNHANDLED
        return controller.moveFocus(delta, rows.lastIndex, wrapMode())
    }

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(delta: Int): InputResult = when (focusedRow()) {
        QuickFriendsRow.Actions -> controller.moveAction(delta)
        QuickFriendsRow.QuayPass -> toggleLeftRight(delta, quayPassEnabled()) { onToggleQuayPass() }
        QuickFriendsRow.AppearOnline ->
            toggleLeftRight(delta, controller.state.value.appearOnline) { controller.toggleAppearOnline() }
        else -> InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val row = focusedRow() ?: return InputResult.UNHANDLED
        activate(row)
        val isToggle = row == QuickFriendsRow.QuayPass || row == QuickFriendsRow.AppearOnline
        return if (isToggle) InputResult.handled(SoundType.TOGGLE) else InputResult.HANDLED
    }

    override fun onLongConfirm(): InputResult {
        val entry = focusedRow() as? QuickFriendsRow.Entry ?: return onConfirm()
        controller.showFriendOptions(entry.friend.id)
        return InputResult.handled(SoundType.OPEN_MODAL)
    }

    fun tapRow(index: Int) {
        val row = rows().getOrNull(index) ?: return
        controller.setFocus(index)
        activate(row)
    }

    fun longPressRow(index: Int) {
        val entry = rows().getOrNull(index) as? QuickFriendsRow.Entry ?: return
        controller.setFocus(index)
        controller.showFriendOptions(entry.friend.id)
    }

    fun openProfile(friend: Friend) {
        controller.dismissModal()
        onOpenProfile(friend)
    }

    fun tapAction(index: Int, action: QuickFriendsAction) {
        controller.setFocus(index)
        controller.setAction(action)
        controller.openAction(action)
    }

    private fun activate(row: QuickFriendsRow) {
        when (row) {
            QuickFriendsRow.QuayPass -> onToggleQuayPass()
            QuickFriendsRow.AppearOnline -> controller.toggleAppearOnline()
            QuickFriendsRow.Actions -> controller.openAction(controller.state.value.action)
            is QuickFriendsRow.Entry -> onOpenProfile(row.friend)
        }
    }
}
