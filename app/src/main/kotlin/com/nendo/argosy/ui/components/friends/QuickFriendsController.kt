package com.nendo.argosy.ui.components.friends

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed class FriendsModal {
    data object None : FriendsModal()
    data object FriendCode : FriendsModal()
    data object AddFriend : FriendsModal()
}

data class QuickFriendsState(
    val socialConnected: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val onlineCount: Int = 0,
    val friendCode: String? = null,
    val friendCodeUrl: String? = null,
    val modal: FriendsModal = FriendsModal.None,
    val focusIndex: Int = 0
)

sealed class QuickFriendsRow(val key: String) {
    data object QuayPass : QuickFriendsRow("quaypass")
    data object FriendCode : QuickFriendsRow("friendCode")
    data object AddFriend : QuickFriendsRow("addFriend")
    data class Entry(val friend: Friend, val position: Int) : QuickFriendsRow("friend_${friend.id}")
}

/**
 * Rows of the quick panel's Friends page in focus order.
 * [QuickFriendsState.focusIndex] indexes this list.
 */
fun quickFriendsRows(showQuayPass: Boolean, state: QuickFriendsState): List<QuickFriendsRow> = buildList {
    if (showQuayPass) add(QuickFriendsRow.QuayPass)
    if (state.socialConnected) {
        add(QuickFriendsRow.FriendCode)
        add(QuickFriendsRow.AddFriend)
        state.friends.forEachIndexed { position, friend -> add(QuickFriendsRow.Entry(friend, position)) }
    }
}

class QuickFriendsController(
    private val socialRepository: SocialRepository,
    scope: CoroutineScope
) {
    private val focusIndex = MutableStateFlow(0)
    private val modal = MutableStateFlow<FriendsModal>(FriendsModal.None)

    val state: StateFlow<QuickFriendsState> = combine(
        socialRepository.connectionState,
        socialRepository.friends,
        socialRepository.friendCode,
        modal,
        focusIndex
    ) { connection, friends, code, openModal, focus ->
        val sorted = sortForDisplay(friends)
        QuickFriendsState(
            socialConnected = connection is SocialConnectionState.Connected,
            friends = sorted,
            onlineCount = sorted.count { it.isOnlineNow },
            friendCode = code?.code,
            friendCodeUrl = code?.url,
            modal = openModal,
            focusIndex = focus
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuickFriendsState()
    )

    fun resetFocus() {
        focusIndex.update { 0 }
    }

    fun setFocus(index: Int) {
        focusIndex.update { index }
    }

    fun moveFocus(delta: Int, maxIndex: Int, wrapMode: MenuWrapMode): InputResult {
        val current = focusIndex.value.coerceIn(0, maxIndex)
        val next = computeWrappedIndex(current, delta, maxIndex, wrapMode)
        focusIndex.update { next }
        return if (next != current) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun showFriendCode() {
        modal.update { FriendsModal.FriendCode }
        if (socialRepository.friendCode.value == null) {
            socialRepository.requestFriendCode()
        }
    }

    fun showAddFriend() {
        modal.update { FriendsModal.AddFriend }
    }

    fun dismissModal() {
        modal.update { FriendsModal.None }
    }

    fun regenerateFriendCode() {
        socialRepository.regenerateFriendCode()
    }

    fun addFriendByCode(code: String) {
        socialRepository.addFriendByCode(code)
    }

    fun toggleFavorite(friendId: String) {
        socialRepository.toggleFavoriteFriend(friendId)
    }

    private fun sortForDisplay(friends: List<Friend>): List<Friend> = friends
        .filter { it.isAccepted }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<Friend> { it.isOnlineNow }
                .thenByDescending { it.isFavorite }
                .thenByDescending { it.presence == PresenceStatus.IN_GAME }
                .thenByDescending { it.presence == PresenceStatus.WATCHING }
                .thenByDescending { it.presence == PresenceStatus.ONLINE }
                .thenBy { it.displayName.lowercase() }
        )
}
