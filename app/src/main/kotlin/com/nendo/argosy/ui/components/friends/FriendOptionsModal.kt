package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens

private class QuickOption(val label: String, val icon: ImageVector, val onSelect: () -> Unit)

@Composable
fun FriendOptionsModal(
    friend: Friend,
    onViewProfile: (Friend) -> Unit,
    onToggleFavorite: (Friend) -> Unit,
    onDismiss: () -> Unit
) {
    QuickOptionsModal(
        title = friend.displayName,
        options = listOf(
            QuickOption(
                stringResource(R.string.ui_quick_friends_options_profile),
                Icons.Default.Person
            ) { onViewProfile(friend) },
            QuickOption(
                stringResource(
                    if (friend.isFavorite) R.string.ui_quick_friends_options_unfavorite
                    else R.string.ui_quick_friends_options_favorite
                ),
                if (friend.isFavorite) Icons.Outlined.StarOutline else Icons.Default.Star
            ) { onToggleFavorite(friend) }
        ),
        onDismiss = onDismiss
    )
}

@Composable
fun ProfileOptionsModal(
    user: SocialUser,
    onViewProfile: () -> Unit,
    onEditAvatar: () -> Unit,
    onShowFriendCode: () -> Unit,
    onDismiss: () -> Unit
) {
    QuickOptionsModal(
        title = user.displayName,
        options = listOf(
            QuickOption(stringResource(R.string.ui_quick_friends_options_profile), Icons.Default.Person, onViewProfile),
            QuickOption(stringResource(R.string.ui_quick_friends_options_edit_avatar), Icons.Default.Brush, onEditAvatar),
            QuickOption(stringResource(R.string.ui_quick_friends_my_code), Icons.Default.QrCode, onShowFriendCode)
        ),
        onDismiss = onDismiss
    )
}

@Composable
private fun QuickOptionsModal(title: String, options: List<QuickOption>, onDismiss: () -> Unit) {
    val focusIndex = remember { mutableIntStateOf(0) }
    val currentOptions by rememberUpdatedState(options)
    val currentDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun move(delta: Int): InputResult {
                val next = focusIndex.intValue + delta
                if (next !in currentOptions.indices) return InputResult.handled(SoundType.BOUNDARY)
                focusIndex.intValue = next
                return InputResult.HANDLED
            }

            private fun close(): InputResult {
                currentDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onUp(): InputResult = move(-1)

            override fun onDown(): InputResult = move(1)

            override fun onLeft(): InputResult = InputResult.HANDLED

            override fun onRight(): InputResult = InputResult.HANDLED

            override fun onConfirm(): InputResult {
                currentOptions.getOrNull(focusIndex.intValue)?.onSelect?.invoke()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult = close()

            override fun onRightStickClick(): InputResult = close()

            override fun onMenu(): InputResult = InputResult.HANDLED

            override fun onSecondaryAction(): InputResult = InputResult.HANDLED

            override fun onContextMenu(): InputResult = InputResult.HANDLED

            override fun onPrevSection(): InputResult = InputResult.HANDLED

            override fun onNextSection(): InputResult = InputResult.HANDLED

            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    NestedModal(title = title, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            options.forEachIndexed { index, option ->
                OptionItem(
                    label = option.label,
                    icon = option.icon,
                    isFocused = focusIndex.intValue == index,
                    onClick = {
                        focusIndex.intValue = index
                        option.onSelect()
                    }
                )
            }
        }
    }
}
