package com.nendo.argosy.ui.components.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun QuickFriendsModals(
    state: QuickFriendsState,
    controller: QuickFriendsController,
    onOpenProfile: (userId: String) -> Unit,
    onEditAvatar: () -> Unit
) {
    val dismiss: () -> Unit = { controller.dismissModal() }
    when (val modal = state.modal) {
        FriendsModal.ProfileOptions -> {
            val user = state.localUser
            if (user == null) {
                LaunchedEffect(Unit) { dismiss() }
            } else {
                ProfileOptionsModal(
                    user = user,
                    onViewProfile = { onOpenProfile(user.id) },
                    onEditAvatar = onEditAvatar,
                    onShowFriendCode = { controller.showFriendCode() },
                    onDismiss = dismiss
                )
            }
        }
        is FriendsModal.FriendOptions -> {
            val friend = state.friends.firstOrNull { it.id == modal.friendId }
            if (friend == null) {
                LaunchedEffect(modal.friendId) { dismiss() }
            } else {
                FriendOptionsModal(
                    friend = friend,
                    onViewProfile = { onOpenProfile(it.id) },
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
