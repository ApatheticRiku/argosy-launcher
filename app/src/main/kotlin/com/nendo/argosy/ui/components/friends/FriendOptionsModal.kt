package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens

private enum class FriendOption { PROFILE, FAVORITE }

@Composable
fun FriendOptionsModal(
    friend: Friend,
    onViewProfile: (Friend) -> Unit,
    onToggleFavorite: (Friend) -> Unit,
    onDismiss: () -> Unit
) {
    val options = FriendOption.entries
    val focusIndex = remember { mutableIntStateOf(0) }

    val activate: (FriendOption) -> Unit = { option ->
        when (option) {
            FriendOption.PROFILE -> onViewProfile(friend)
            FriendOption.FAVORITE -> onToggleFavorite(friend)
        }
    }

    val inputHandler = remember(friend, onViewProfile, onToggleFavorite, onDismiss) {
        object : InputHandler {
            private fun move(delta: Int): InputResult {
                val next = focusIndex.intValue + delta
                if (next !in options.indices) return InputResult.handled(SoundType.BOUNDARY)
                focusIndex.intValue = next
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult = move(-1)

            override fun onDown(): InputResult = move(1)

            override fun onLeft(): InputResult = InputResult.HANDLED

            override fun onRight(): InputResult = InputResult.HANDLED

            override fun onConfirm(): InputResult {
                activate(options[focusIndex.intValue])
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED

            override fun onSecondaryAction(): InputResult = InputResult.HANDLED

            override fun onContextMenu(): InputResult = InputResult.HANDLED

            override fun onPrevSection(): InputResult = InputResult.HANDLED

            override fun onNextSection(): InputResult = InputResult.HANDLED

            override fun onLongConfirm(): InputResult = InputResult.HANDLED

            override fun onRightStickClick(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    NestedModal(title = friend.displayName, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            options.forEachIndexed { index, option ->
                val focused = focusIndex.intValue == index
                val select = {
                    focusIndex.intValue = index
                    activate(option)
                }
                when (option) {
                    FriendOption.PROFILE -> OptionItem(
                        label = stringResource(R.string.ui_quick_friends_options_profile),
                        icon = Icons.Default.Person,
                        isFocused = focused,
                        onClick = select
                    )
                    FriendOption.FAVORITE -> OptionItem(
                        label = stringResource(
                            if (friend.isFavorite) R.string.ui_quick_friends_options_unfavorite
                            else R.string.ui_quick_friends_options_favorite
                        ),
                        icon = if (friend.isFavorite) Icons.Outlined.StarOutline else Icons.Default.Star,
                        isFocused = focused,
                        onClick = select
                    )
                }
            }
        }
    }
}
