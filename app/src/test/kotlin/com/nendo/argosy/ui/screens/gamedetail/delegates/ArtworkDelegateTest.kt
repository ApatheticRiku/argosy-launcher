package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.input.SoundFeedbackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDelegateTest {

    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val delegate = ArtworkDelegate(
        imageCacheManager = imageCacheManager,
        notificationManager = notificationManager,
        soundManager = mockk<SoundFeedbackManager>(relaxed = true)
    )

    private val picks = listOf(
        ArtworkRow.Pick(ArtSlot.COVER),
        ArtworkRow.Pick(ArtSlot.BACKGROUND),
        ArtworkRow.Pick(ArtSlot.LOGO)
    )

    @Test
    fun `without overrides the menu offers only the three pick rows`() {
        assertEquals(picks, artworkRows(emptySet()))
    }

    @Test
    fun `each overridden slot adds its revert row after the pick rows`() {
        assertEquals(
            picks + ArtworkRow.Revert(ArtSlot.LOGO),
            artworkRows(setOf(ArtSlot.LOGO))
        )
        assertEquals(
            picks + listOf(
                ArtworkRow.Revert(ArtSlot.COVER),
                ArtworkRow.Revert(ArtSlot.BACKGROUND),
                ArtworkRow.Revert(ArtSlot.LOGO)
            ),
            artworkRows(setOf(ArtSlot.LOGO, ArtSlot.COVER, ArtSlot.BACKGROUND))
        )
    }

    @Test
    fun `opening the menu focuses the first row`() {
        delegate.showArtworkMenu()
        delegate.moveFocus(1, 3)
        delegate.dismissArtworkMenu()

        delegate.showArtworkMenu()

        assertTrue(delegate.state.value.showArtworkMenu)
        assertEquals(0, delegate.state.value.artworkFocusIndex)
    }

    @Test
    fun `focus wraps past both ends`() {
        delegate.showArtworkMenu()

        delegate.moveFocus(-1, 4)
        assertEquals(3, delegate.state.value.artworkFocusIndex)

        delegate.moveFocus(1, 4)
        assertEquals(0, delegate.state.value.artworkFocusIndex)
    }

    @Test
    fun `the focused row resolves against the current overrides`() {
        delegate.showArtworkMenu()
        delegate.moveFocus(-1, artworkRows(setOf(ArtSlot.BACKGROUND)).size)

        assertEquals(ArtworkRow.Revert(ArtSlot.BACKGROUND), delegate.focusedRow(setOf(ArtSlot.BACKGROUND)))
    }

    @Test
    fun `back from the menu closes it`() {
        delegate.showArtworkMenu()

        delegate.dismissArtworkMenu()

        assertFalse(delegate.state.value.showArtworkMenu)
    }

    @Test
    fun `leaving for the picker and coming back keeps the focused row`() {
        delegate.showArtworkMenu()
        delegate.moveFocus(2, 3)

        delegate.leaveForPicker()
        assertFalse(delegate.state.value.showArtworkMenu)

        delegate.returnFromPicker(3)
        assertTrue(delegate.state.value.showArtworkMenu)
        assertEquals(2, delegate.state.value.artworkFocusIndex)
    }

    @Test
    fun `losing the focused revert row pulls focus onto the last remaining row`() {
        delegate.showArtworkMenu()
        delegate.moveFocus(-1, 4)

        delegate.clampFocus(3)

        assertEquals(2, delegate.state.value.artworkFocusIndex)
    }

    @Test
    fun `a failed download raises the artwork error notice`() = runTest {
        coEvery { imageCacheManager.applyArtOverride(7L, ArtSlot.LOGO, any()) } returns false
        var finished = false

        delegate.applyOverride(this, 7L, ArtSlot.LOGO, "https://example.test/logo.png") { finished = true }
        advanceUntilIdle()

        assertTrue(finished)
        verify {
            notificationManager.show(
                any(),
                NotificationText.Res(R.string.gamedetail_notice_artwork_failed),
                NotificationType.ERROR,
                any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `a local path is read as a file and a success raises no notice`() = runTest {
        coEvery { imageCacheManager.applyArtOverrideFromFile(7L, ArtSlot.BACKGROUND, any()) } returns true

        delegate.applyOverride(this, 7L, ArtSlot.BACKGROUND, "/storage/emulated/0/Pictures/bg.png") {}
        advanceUntilIdle()

        coVerify { imageCacheManager.applyArtOverrideFromFile(7L, ArtSlot.BACKGROUND, "/storage/emulated/0/Pictures/bg.png") }
        coVerify(exactly = 0) { imageCacheManager.applyArtOverride(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.show(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `revert clears only the chosen slot`() = runTest {
        delegate.revertOverride(this, 7L, ArtSlot.COVER) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { imageCacheManager.clearArtOverride(7L, ArtSlot.COVER) }
        coVerify(exactly = 0) { imageCacheManager.clearArtOverride(7L, ArtSlot.BACKGROUND) }
        coVerify(exactly = 0) { imageCacheManager.clearArtOverride(7L, ArtSlot.LOGO) }
    }
}
