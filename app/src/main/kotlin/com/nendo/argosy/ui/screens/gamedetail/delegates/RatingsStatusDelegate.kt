package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.RatingType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingsStatusState(
    val showRatingPicker: Boolean = false,
    val ratingPickerType: RatingType = RatingType.OPINION,
    val ratingPickerValue: Int = 0,
    val showStatusPicker: Boolean = false,
    val statusPickerValue: String? = null,
    val showRatingsStatusMenu: Boolean = false,
    val ratingsStatusFocusIndex: Int = 0
)

private const val RATINGS_STATUS_ROW_COUNT = 4

class RatingsStatusDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager,
    private val hapticManager: HapticFeedbackManager
) {
    private val _state = MutableStateFlow(RatingsStatusState())
    val state: StateFlow<RatingsStatusState> = _state.asStateFlow()

    fun reset() {
        _state.value = RatingsStatusState()
    }

    fun showRatingPicker(type: RatingType, currentValue: Int) {
        _state.update {
            it.copy(
                showRatingsStatusMenu = false,
                showRatingPicker = true,
                ratingPickerType = type,
                ratingPickerValue = currentValue
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingPicker() {
        _state.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun showRatingsStatusMenu() {
        _state.update {
            it.copy(
                showRatingsStatusMenu = true,
                ratingsStatusFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingsStatusMenu() {
        _state.update {
            it.copy(showRatingsStatusMenu = false)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeRatingsStatusFocus(delta: Int) {
        _state.update { state ->
            val newIndex = (state.ratingsStatusFocusIndex + delta).coerceIn(0, RATINGS_STATUS_ROW_COUNT - 1)
            state.copy(ratingsStatusFocusIndex = newIndex)
        }
    }

    fun confirmRatingsStatusSelection() {
        when (_state.value.ratingsStatusFocusIndex) {
            0 -> {} // caller should pass current game values
            1 -> {}
            2 -> {}
        }
    }

    fun getRatingsStatusAction(): Int = _state.value.ratingsStatusFocusIndex

    /**
     * Steps the picker value and returns whether it moved. A step clamped at either end leaves the
     * value unchanged, returns false and fires [HapticPattern.BOUNDARY_HIT].
     */
    fun changeRatingValue(direction: Int): Boolean {
        var moved = false
        _state.update { state ->
            val next = state.ratingPickerType.stepFrom(state.ratingPickerValue, direction)
            moved = next != state.ratingPickerValue
            state.copy(ratingPickerValue = next)
        }
        if (!moved && direction != 0) hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        return moved
    }

    fun setRatingValue(value: Int) {
        _state.update { it.copy(ratingPickerValue = it.ratingPickerType.clamp(value)) }
    }

    fun confirmRating(scope: CoroutineScope, gameId: Long, onSuccess: () -> Unit) {
        val state = _state.value
        val value = state.ratingPickerValue
        val type = state.ratingPickerType

        scope.launch {
            val result = when (type) {
                RatingType.OPINION -> romMRepository.updateUserRating(gameId, value)
                RatingType.DIFFICULTY -> romMRepository.updateUserDifficulty(gameId, value)
                RatingType.PROGRESS -> romMRepository.updateCompletion(gameId, value)
            }

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    val message = when (type) {
                        RatingType.OPINION -> R.string.gamedetail_notice_rating_saved
                        RatingType.DIFFICULTY -> R.string.gamedetail_notice_difficulty_saved
                        RatingType.PROGRESS -> R.string.gamedetail_notice_progress_saved
                    }
                    notificationManager.showSuccess(NotificationText.Res(message))
                    onSuccess()
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
                }
            }
            _state.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        }
    }

    fun showStatusPicker(currentStatus: String?) {
        _state.update {
            it.copy(
                showRatingsStatusMenu = false,
                showStatusPicker = true,
                statusPickerValue = currentStatus
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissStatusPicker() {
        _state.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeStatusValue(delta: Int) {
        _state.update { state ->
            val newValue = if (delta > 0) {
                com.nendo.argosy.domain.model.CompletionStatus.cycleNext(state.statusPickerValue)
            } else {
                com.nendo.argosy.domain.model.CompletionStatus.cyclePrev(state.statusPickerValue)
            }
            state.copy(statusPickerValue = newValue)
        }
    }

    fun selectStatus(value: String) {
        _state.update { it.copy(statusPickerValue = value) }
    }

    fun confirmStatus(scope: CoroutineScope, gameId: Long, onSuccess: () -> Unit) {
        val value = _state.value.statusPickerValue

        scope.launch {
            val result = romMRepository.updateUserStatus(gameId, value)

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.gamedetail_notice_status_saved)
                    )
                    onSuccess()
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
                }
            }
            _state.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        }
    }
}
