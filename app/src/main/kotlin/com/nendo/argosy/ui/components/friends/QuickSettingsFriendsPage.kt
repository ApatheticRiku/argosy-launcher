package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.ui.components.QuickSettingItem
import com.nendo.argosy.ui.components.QuickSettingToggle
import com.nendo.argosy.ui.components.QuickSettingsGroupHeader
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.quickFocusBackground
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun QuickSettingsFriendsPage(
    state: QuickFriendsState,
    showQuayPass: Boolean,
    quayPassEnabled: Boolean,
    onRowClick: (Int) -> Unit
) {
    val rows = remember(showQuayPass, state) { quickFriendsRows(showQuayPass, state) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusIndex, rows.size) {
        if (state.focusIndex in rows.indices) listState.animateScrollToItemCentered(state.focusIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(rows.size, key = { rows[it].key }) { index ->
            val isFocused = index == state.focusIndex
            when (val row = rows[index]) {
                QuickFriendsRow.QuayPass -> QuickSettingToggle(
                    icon = if (quayPassEnabled) QuayPassIcons.On else QuayPassIcons.Off,
                    label = stringResource(R.string.ui_quick_settings_quaypass),
                    isEnabled = quayPassEnabled,
                    isFocused = isFocused,
                    onClick = { onRowClick(index) }
                )
                QuickFriendsRow.FriendCode -> QuickSettingItem(
                    icon = Icons.Default.QrCode,
                    label = stringResource(R.string.ui_quick_friends_my_code),
                    value = "",
                    isFocused = isFocused,
                    onClick = { onRowClick(index) }
                )
                QuickFriendsRow.AddFriend -> QuickSettingItem(
                    icon = Icons.Default.PersonAdd,
                    label = stringResource(R.string.ui_quick_friends_add_friend),
                    value = "",
                    isFocused = isFocused,
                    onClick = { onRowClick(index) }
                )
                is QuickFriendsRow.Entry -> Column {
                    FriendSectionLabel(
                        position = row.position,
                        onlineCount = state.onlineCount,
                        offlineCount = state.friends.size - state.onlineCount
                    )
                    FriendRow(
                        friend = row.friend,
                        isFocused = isFocused,
                        onClick = { onRowClick(index) }
                    )
                }
            }
        }
        if (state.socialConnected && state.friends.isEmpty()) {
            item(key = "friendsEmpty") {
                Text(
                    text = stringResource(R.string.ui_drawer_friends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
                )
            }
        }
    }
}

@Composable
private fun FriendSectionLabel(position: Int, onlineCount: Int, offlineCount: Int) {
    when {
        position == 0 && onlineCount > 0 -> QuickSettingsGroupHeader(
            title = stringResource(R.string.ui_drawer_friends_online, onlineCount)
        )
        position == onlineCount && offlineCount > 0 -> QuickSettingsGroupHeader(
            title = stringResource(R.string.ui_drawer_friends_offline, offlineCount)
        )
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, bottomStart = Dimens.radiusMd)
    val presenceColor = ColorTokens.Domain.Presence.online

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd)
            .clip(shape)
            .background(quickFocusBackground(isFocused))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        SocialAvatar(
            displayName = friend.displayName,
            avatarColor = friend.avatarColor,
            size = Dimens.iconLg,
            showOnlineDot = friend.isOnlineNow,
            avatarPngBase64 = friend.quayPassAvatar
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (friend.presence == PresenceStatus.IN_GAME) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconXs),
                        tint = presenceColor
                    )
                    Text(
                        text = friendActivityLabel(friend),
                        style = MaterialTheme.typography.labelSmall,
                        color = presenceColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (friend.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.ui_drawer_friend_favorite),
                modifier = Modifier.size(Dimens.iconXs),
                tint = ColorTokens.Domain.favoriteStar
            )
        }
    }
}

@Composable
private fun friendActivityLabel(friend: Friend): String {
    val game = friend.currentGame ?: return stringResource(R.string.ui_drawer_friend_in_game)
    return if (game.netplaySession != null) {
        stringResource(R.string.ui_drawer_friend_hosting, game.title)
    } else {
        stringResource(R.string.ui_drawer_friend_playing, game.title)
    }
}
