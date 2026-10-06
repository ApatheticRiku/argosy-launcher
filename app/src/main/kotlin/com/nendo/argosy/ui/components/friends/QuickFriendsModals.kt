package com.nendo.argosy.ui.components.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nendo.argosy.data.social.Friend

@Composable
fun QuickFriendsModals(
    state: QuickFriendsState,
    controller: QuickFriendsController,
    onOpenProfile: (Friend) -> Unit
) {
    val dismiss: () -> Unit = { controller.dismissModal() }
    when (val modal = state.modal) {
        is FriendsModal.FriendOptions -> {
            val friend = state.friends.firstOrNull { it.id == modal.friendId }
            if (friend == null) {
                LaunchedEffect(modal.friendId) { dismiss() }
            } else {
                FriendOptionsModal(
                    friend = friend,
                    onViewProfile = onOpenProfile,
                    onToggleFavorite = { controller.toggleFavorite(it.id) },
                    onDismiss = dismiss
                )
            }
        }
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
