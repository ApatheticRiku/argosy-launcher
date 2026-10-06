package com.nendo.argosy.ui.components.friends

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class FriendsModal {
    data object None : FriendsModal()
    data object FriendCode : FriendsModal()
    data object AddFriend : FriendsModal()
}

enum class QuickFriendsAction { MY_CODE, ADD_FRIEND }

data class QuickFriendsState(
    val socialConnected: Boolean = false,
    val localUser: SocialUser? = null,
    val friends: List<Friend> = emptyList(),
    val onlineCount: Int = 0,
    val friendCode: String? = null,
    val friendCodeUrl: String? = null,
    val modal: FriendsModal = FriendsModal.None,
    val focusIndex: Int = 0,
    val action: QuickFriendsAction = QuickFriendsAction.MY_CODE,
    val appearOnline: Boolean = true
)

sealed class QuickFriendsRow(val key: String) {
    data object QuayPass : QuickFriendsRow("quaypass")
    data object AppearOnline : QuickFriendsRow("appear_online")
    data object Actions : QuickFriendsRow("actions")
    data class Entry(val friend: Friend, val position: Int) : QuickFriendsRow("friend_${friend.id}")
}

/**
 * Rows of the quick panel's Friends page in focus order.
 * [QuickFriendsState.focusIndex] indexes this list.
 */
fun quickFriendsRows(showQuayPass: Boolean, state: QuickFriendsState): List<QuickFriendsRow> = buildList {
    if (showQuayPass) add(QuickFriendsRow.QuayPass)
    if (state.socialConnected) {
        add(QuickFriendsRow.AppearOnline)
        add(QuickFriendsRow.Actions)
        state.friends.forEachIndexed { position, friend -> add(QuickFriendsRow.Entry(friend, position)) }
    }
}

private data class QuickFriendsFocus(
    val index: Int = 0,
    val action: QuickFriendsAction = QuickFriendsAction.MY_CODE
)

class QuickFriendsController(
    private val socialRepository: SocialRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    private val focus = MutableStateFlow(QuickFriendsFocus())
    private val modal = MutableStateFlow<FriendsModal>(FriendsModal.None)
    private val appearOnline = preferencesRepository.userPreferences
        .map { it.socialOnlineStatusEnabled }
        .distinctUntilChanged()

    val state: StateFlow<QuickFriendsState> = combine(
        combine(socialRepository.connectionState, appearOnline, ::Pair),
        socialRepository.friends,
        socialRepository.friendCode,
        modal,
        focus
    ) { (connection, online), friends, code, openModal, focused ->
        val sorted = sortForDisplay(friends)
        val connected = connection as? SocialConnectionState.Connected
        QuickFriendsState(
            socialConnected = connected != null,
            localUser = connected?.user,
            friends = sorted,
            onlineCount = sorted.count { it.isOnlineNow },
            friendCode = code?.code,
            friendCodeUrl = code?.url,
            modal = openModal,
            focusIndex = focused.index,
            action = focused.action,
            appearOnline = online
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuickFriendsState()
    )

    fun resetFocus() {
        focus.update { QuickFriendsFocus() }
    }

    fun setFocus(index: Int) {
        focus.update { it.copy(index = index) }
    }

    fun setAction(action: QuickFriendsAction) {
        focus.update { it.copy(action = action) }
    }

    fun moveAction(delta: Int): InputResult {
        val actions = QuickFriendsAction.entries
        val current = focus.value.action.ordinal
        val next = current + delta
        if (next !in actions.indices) return InputResult.handled(SoundType.BOUNDARY)
        focus.update { it.copy(action = actions[next]) }
        return InputResult.HANDLED
    }

    fun moveFocus(delta: Int, maxIndex: Int, wrapMode: MenuWrapMode): InputResult {
        val current = focus.value.index.coerceIn(0, maxIndex)
        val next = computeWrappedIndex(current, delta, maxIndex, wrapMode)
        focus.update { it.copy(index = next) }
        return if (next != current) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun openAction(action: QuickFriendsAction) {
        when (action) {
            QuickFriendsAction.MY_CODE -> showFriendCode()
            QuickFriendsAction.ADD_FRIEND -> showAddFriend()
        }
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

    fun setAppearOnline(enabled: Boolean) {
        scope.launch { preferencesRepository.setSocialOnlineStatusEnabled(enabled) }
    }

    fun toggleAppearOnline() = setAppearOnline(!state.value.appearOnline)

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
