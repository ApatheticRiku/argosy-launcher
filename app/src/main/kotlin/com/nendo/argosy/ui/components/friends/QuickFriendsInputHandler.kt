package com.nendo.argosy.ui.components.friends

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class QuickFriendsInputHandler(
    private val controller: QuickFriendsController,
    private val showQuayPass: () -> Boolean,
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

    override fun onConfirm(): InputResult {
        val row = focusedRow() ?: return InputResult.UNHANDLED
        activate(row)
        return if (row == QuickFriendsRow.QuayPass) InputResult.handled(SoundType.TOGGLE) else InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        if (!controller.state.value.socialConnected) return InputResult.UNHANDLED
        (focusedRow() as? QuickFriendsRow.Entry)?.let { controller.toggleFavorite(it.friend.id) }
        return InputResult.HANDLED
    }

    fun tapRow(index: Int) {
        val row = rows().getOrNull(index) ?: return
        controller.setFocus(index)
        activate(row)
    }

    private fun activate(row: QuickFriendsRow) {
        when (row) {
            QuickFriendsRow.QuayPass -> onToggleQuayPass()
            QuickFriendsRow.FriendCode -> controller.showFriendCode()
            QuickFriendsRow.AddFriend -> controller.showAddFriend()
            is QuickFriendsRow.Entry -> onOpenProfile(row.friend)
        }
    }
}
