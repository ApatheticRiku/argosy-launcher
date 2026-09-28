package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.gamedetail.RatingType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RatingsStatusDelegateProgressTest {

    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val delegate = RatingsStatusDelegate(
        context = mockk(relaxed = true),
        romMRepository = romMRepository,
        notificationManager = mockk(relaxed = true),
        soundManager = mockk<SoundFeedbackManager>(relaxed = true)
    )

    @Test
    fun `progress steps by five and stops at 100`() {
        delegate.showRatingPicker(RatingType.PROGRESS, 90)

        delegate.changeRatingValue(1)
        assertEquals(95, delegate.state.value.ratingPickerValue)
        delegate.changeRatingValue(1)
        assertEquals(100, delegate.state.value.ratingPickerValue)
        delegate.changeRatingValue(1)
        assertEquals(100, delegate.state.value.ratingPickerValue)
    }

    @Test
    fun `progress steps down to 0 and stops there`() {
        delegate.showRatingPicker(RatingType.PROGRESS, 5)

        delegate.changeRatingValue(-1)
        assertEquals(0, delegate.state.value.ratingPickerValue)
        delegate.changeRatingValue(-1)
        assertEquals(0, delegate.state.value.ratingPickerValue)
    }

    @Test
    fun `an off-grid server value snaps to the next multiple of five in either direction`() {
        delegate.showRatingPicker(RatingType.PROGRESS, 37)
        delegate.changeRatingValue(1)
        assertEquals(40, delegate.state.value.ratingPickerValue)

        delegate.showRatingPicker(RatingType.PROGRESS, 37)
        delegate.changeRatingValue(-1)
        assertEquals(35, delegate.state.value.ratingPickerValue)
    }

    @Test
    fun `rating still steps by one within 0 to 10`() {
        delegate.showRatingPicker(RatingType.OPINION, 9)

        delegate.changeRatingValue(1)
        delegate.changeRatingValue(1)
        assertEquals(10, delegate.state.value.ratingPickerValue)

        delegate.showRatingPicker(RatingType.DIFFICULTY, 1)
        delegate.changeRatingValue(-1)
        delegate.changeRatingValue(-1)
        assertEquals(0, delegate.state.value.ratingPickerValue)
    }

    @Test
    fun `setting a value clamps to the picker type`() {
        delegate.showRatingPicker(RatingType.PROGRESS, 0)
        delegate.setRatingValue(150)
        assertEquals(100, delegate.state.value.ratingPickerValue)

        delegate.showRatingPicker(RatingType.OPINION, 0)
        delegate.setRatingValue(15)
        assertEquals(10, delegate.state.value.ratingPickerValue)
    }

    @Test
    fun `confirming progress writes completion, never rating or difficulty`() = runTest {
        coEvery { romMRepository.updateCompletion(any(), any()) } returns RomMResult.Success(Unit)
        delegate.showRatingPicker(RatingType.PROGRESS, 40)
        delegate.changeRatingValue(1)

        delegate.confirmRating(this, gameId = 7L) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { romMRepository.updateCompletion(7L, 45) }
        coVerify(exactly = 0) { romMRepository.updateUserRating(any(), any()) }
        coVerify(exactly = 0) { romMRepository.updateUserDifficulty(any(), any()) }
    }

    @Test
    fun `confirming progress at 0 clears completion`() = runTest {
        coEvery { romMRepository.updateCompletion(any(), any()) } returns RomMResult.Success(Unit)
        delegate.showRatingPicker(RatingType.PROGRESS, 5)
        delegate.changeRatingValue(-1)

        delegate.confirmRating(this, gameId = 7L) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { romMRepository.updateCompletion(7L, 0) }
    }
}
