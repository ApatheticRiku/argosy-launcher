package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.repository.SiblingGroupRepository
import com.nendo.argosy.domain.model.SiblingGroup
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.domain.model.SiblingPickChange
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SiblingChoiceDelegate"

enum class SiblingChoicePurpose { DOWNLOAD, ACTIVE_VARIANT }

/**
 * The open group chooser. For [SiblingChoicePurpose.ACTIVE_VARIANT] row 0 is "Automatic" and the
 * members follow it; for [SiblingChoicePurpose.DOWNLOAD] the rows are the members alone.
 */
data class SiblingChoiceState(
    val purpose: SiblingChoicePurpose,
    val gameId: Long,
    val group: SiblingGroup? = null,
    val focusIndex: Int = 0,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
) {
    val members: List<SiblingGroupMember> get() = group?.members.orEmpty()

    val hasAutomaticRow: Boolean get() = purpose == SiblingChoicePurpose.ACTIVE_VARIANT

    val rowCount: Int
        get() = if (members.size > 1) members.size + (if (hasAutomaticRow) 1 else 0) else 0

    val isAutomaticFocused: Boolean get() = hasAutomaticRow && focusIndex == 0

    fun rowIndexOf(memberIndex: Int): Int = if (hasAutomaticRow) memberIndex + 1 else memberIndex

    fun memberAt(rowIndex: Int): SiblingGroupMember? =
        members.getOrNull(if (hasAutomaticRow) rowIndex - 1 else rowIndex)
}

class SiblingChoiceDelegate @Inject constructor(
    private val siblingGroupRepository: SiblingGroupRepository,
    private val soundManager: SoundFeedbackManager,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow<SiblingChoiceState?>(null)
    val state: StateFlow<SiblingChoiceState?> = _state.asStateFlow()

    val pickChanges: SharedFlow<SiblingPickChange> get() = siblingGroupRepository.pickChanges

    private var onDownloadChosen: ((Long) -> Unit)? = null
    private var onPickChanged: ((Long) -> Unit)? = null
    private var loadJob: Job? = null

    suspend fun hasChoice(gameId: Long): Boolean = loadGroup(gameId)?.hasChoice == true

    /**
     * [download] receives the game to fetch: [gameId] itself for a single-member or unreadable group,
     * or the chosen member, already stored as this device's pick.
     */
    fun requestDownload(scope: CoroutineScope, gameId: Long, download: (Long) -> Unit) {
        if (_state.value != null) return
        scope.launch {
            val group = loadGroup(gameId)
            if (group == null || !group.hasChoice) {
                download(gameId)
                return@launch
            }
            onDownloadChosen = download
            onPickChanged = null
            val shownIndex = group.members.indexOfFirst { it.isShown }.coerceAtLeast(0)
            _state.value = SiblingChoiceState(
                purpose = SiblingChoicePurpose.DOWNLOAD,
                gameId = gameId,
                group = group,
                focusIndex = shownIndex
            )
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    fun openActiveVariant(scope: CoroutineScope, gameId: Long, onShownGameChanged: (Long) -> Unit) {
        onPickChanged = onShownGameChanged
        onDownloadChosen = null
        _state.value = SiblingChoiceState(
            purpose = SiblingChoicePurpose.ACTIVE_VARIANT,
            gameId = gameId,
            isLoading = true
        )
        soundManager.play(SoundType.OPEN_MODAL)
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = try {
                Result.success(siblingGroupRepository.groupFor(gameId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, "openActiveVariant: group for $gameId unavailable", e)
                Result.failure(e)
            }
            _state.update { current ->
                if (current == null || current.gameId != gameId || !current.isLoading) return@update current
                val group = result.getOrNull()
                val pickedIndex = group?.members?.indexOfFirst { it.isPicked } ?: -1
                current.copy(
                    group = group,
                    isLoading = false,
                    loadFailed = result.isFailure,
                    focusIndex = if (pickedIndex >= 0) current.rowIndexOf(pickedIndex) else 0
                )
            }
        }
    }

    fun moveFocus(delta: Int) {
        _state.update { current ->
            if (current == null || current.rowCount == 0) return@update current
            current.copy(focusIndex = (current.focusIndex + delta).mod(current.rowCount))
        }
    }

    fun setFocus(index: Int) {
        _state.update { current ->
            if (current == null || index !in 0 until current.rowCount) return@update current
            current.copy(focusIndex = index)
        }
    }

    fun confirm(scope: CoroutineScope) {
        val current = _state.value ?: return
        if (current.isLoading) return
        val group = current.group
        if (group == null || current.rowCount == 0) {
            dismiss()
            return
        }
        when (current.purpose) {
            SiblingChoicePurpose.DOWNLOAD -> confirmDownload(scope, current)
            SiblingChoicePurpose.ACTIVE_VARIANT -> confirmActiveVariant(scope, current, group)
        }
    }

    fun dismiss() {
        if (_state.value == null) return
        reset()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun reset() {
        loadJob?.cancel()
        loadJob = null
        onDownloadChosen = null
        onPickChanged = null
        _state.value = null
    }

    private fun confirmDownload(scope: CoroutineScope, current: SiblingChoiceState) {
        val member = current.memberAt(current.focusIndex) ?: return
        val download = onDownloadChosen
        reset()
        scope.launch {
            try {
                siblingGroupRepository.setPick(member.gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, "confirmDownload: pick for ${member.gameId} not stored", e)
            }
            download?.invoke(member.gameId)
        }
    }

    private fun confirmActiveVariant(scope: CoroutineScope, current: SiblingChoiceState, group: SiblingGroup) {
        val member = current.memberAt(current.focusIndex)
        if (member == null && !current.isAutomaticFocused) return
        val onChanged = onPickChanged
        reset()
        scope.launch {
            try {
                val shownGameId = if (member == null) {
                    siblingGroupRepository.clearPick(group.groupKey)
                    siblingGroupRepository.groupFor(current.gameId)?.shownMember?.gameId ?: current.gameId
                } else {
                    siblingGroupRepository.setPick(member.gameId)
                    member.gameId
                }
                onChanged?.invoke(shownGameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, "confirmActiveVariant: pick for ${group.groupKey} not stored", e)
                notificationManager.showError(NotificationText.Res(R.string.ui_sibling_choice_notice_pick_failed))
            }
        }
    }

    private suspend fun loadGroup(gameId: Long): SiblingGroup? = try {
        siblingGroupRepository.groupFor(gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "loadGroup: group for $gameId unavailable", e)
        null
    }
}
