package com.nendo.argosy.ui.components.friends

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.input.toggleLeftRight

class QuickFriendsInputHandler(
    private val controller: QuickFriendsController,
    private val showQuayPass: () -> Boolean,
    private val quayPassEnabled: () -> Boolean,
    private val wrapMode: () -> MenuWrapMode,
    private val onToggleQuayPass: () -> Unit,
    private val onOpenProfile: (userId: String) -> Unit,
    private val onEditAvatar: () -> Unit
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
        val row = focusedRow() ?: return InputResult.UNHANDLED
        if (!showOptions(row)) return onConfirm()
        return InputResult.handled(SoundType.OPEN_MODAL)
    }

    fun tapRow(index: Int) {
        val row = rows().getOrNull(index) ?: return
        controller.setFocus(index)
        activate(row)
    }

    fun longPressRow(index: Int) {
        val row = rows().getOrNull(index) ?: return
        controller.setFocus(index)
        showOptions(row)
    }

    fun openProfile(userId: String) {
        controller.dismissModal()
        onOpenProfile(userId)
    }

    fun editAvatar() {
        controller.dismissModal()
        onEditAvatar()
    }

    fun tapAction(index: Int, action: QuickFriendsAction) {
        controller.setFocus(index)
        controller.setAction(action)
        controller.openAction(action)
    }

    private fun showOptions(row: QuickFriendsRow): Boolean = when (row) {
        QuickFriendsRow.Profile -> {
            controller.showProfileOptions()
            true
        }
        is QuickFriendsRow.Entry -> {
            controller.showFriendOptions(row.friend.id)
            true
        }
        else -> false
    }

    private fun activate(row: QuickFriendsRow) {
        when (row) {
            QuickFriendsRow.Profile -> controller.state.value.localUser?.let { onOpenProfile(it.id) }
            QuickFriendsRow.QuayPass -> onToggleQuayPass()
            QuickFriendsRow.AppearOnline -> controller.toggleAppearOnline()
            QuickFriendsRow.Actions -> controller.openAction(controller.state.value.action)
            is QuickFriendsRow.Entry -> onOpenProfile(row.friend.id)
        }
    }
}
