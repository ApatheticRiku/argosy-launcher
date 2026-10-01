package com.nendo.argosy.ui.components.friends

import androidx.compose.runtime.Composable

@Composable
fun QuickFriendsModals(
    state: QuickFriendsState,
    controller: QuickFriendsController
) {
    val dismiss: () -> Unit = { controller.dismissModal() }
    when (state.modal) {
        FriendsModal.FriendCode -> {
            FriendCodeModal(
                code = state.friendCode,
                url = state.friendCodeUrl,
                onRegenerate = { controller.regenerateFriendCode() },
                onDismiss = dismiss
            )
        }
        FriendsModal.AddFriend -> {
            AddFriendModal(
                onSubmit = { code ->
                    controller.addFriendByCode(code)
                    dismiss()
                },
                onDismiss = dismiss
            )
        }
        FriendsModal.None -> Unit
    }
}
