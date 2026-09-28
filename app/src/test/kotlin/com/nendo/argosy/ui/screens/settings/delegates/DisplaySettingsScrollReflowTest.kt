package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.CustomGridShapeStore
import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.ScrollArrangement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val OWNER = 4L

@OptIn(ExperimentalCoroutinesApi::class)
class DisplaySettingsScrollReflowTest {

    private val homeTileRepository = mockk<HomeTileRepository>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true).also {
        coEvery { it.getRommUserId() } returns OWNER
    }

    private val delegate = DisplaySettingsDelegate(
        preferencesRepository = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        ledController = mockk(relaxed = true),
        screenCaptureManager = mockk(relaxed = true),
        customGridShapeStore = CustomGridShapeStore(),
        homeTileRepository = homeTileRepository,
        syncPreferencesRepository = syncPreferences
    )

    private fun start(grid: CustomGridConfig): HomeLayoutSettings {
        val settings = HomeLayoutSettings(customGrid = grid)
        delegate.updateState(delegate.state.value.copy(homeLayout = settings))
        return settings
    }

    private fun TestScope.change(from: HomeLayoutSettings, grid: CustomGridConfig) {
        delegate.setHomeLayout(this, from.copy(customGrid = grid))
        advanceUntilIdle()
    }

    @Test
    fun `fewer columns on a vertical scroll grid reflow its tiles for the signed-in owner`() = runTest {
        val scrolling = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Scroll)
        val settings = start(scrolling)

        change(settings, scrolling.copy(columns = GridAxis.Fixed(3)))

        coVerify(exactly = 1) {
            homeTileRepository.reflowScroll(
                OWNER,
                CustomGridLayout(CustomGridShape(4, 4), HomeScrollAxis.VERTICAL),
                CustomGridLayout(CustomGridShape(3, 3), HomeScrollAxis.VERTICAL)
            )
        }
    }

    @Test
    fun `flipping the scroll direction reflows`() = runTest {
        val vertical = CustomGridConfig(columns = GridAxis.Fixed(3), rows = GridAxis.Scroll)
        val settings = start(vertical)

        change(settings, CustomGridConfig(columns = GridAxis.Scroll, rows = GridAxis.Fixed(3)))

        coVerify(exactly = 1) { homeTileRepository.reflowScroll(OWNER, any(), any()) }
    }

    @Test
    fun `entering scroll mode does not reflow`() = runTest {
        val paged = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Fixed(3))
        val settings = start(paged)

        change(settings, paged.copy(rows = GridAxis.Scroll))

        coVerify(exactly = 0) { homeTileRepository.reflowScroll(any(), any(), any()) }
    }

    @Test
    fun `leaving scroll mode does not reflow`() = runTest {
        val scrolling = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Scroll)
        val settings = start(scrolling)

        change(settings, scrolling.copy(rows = GridAxis.Fixed(3)))

        coVerify(exactly = 0) { homeTileRepository.reflowScroll(any(), any(), any()) }
    }

    @Test
    fun `flipping the direction through the paged grid reflows from the arrangement it left`() = runTest {
        val vertical = CustomGridConfig(columns = GridAxis.Fixed(3), rows = GridAxis.Scroll)
        start(vertical)

        val paged = vertical.copy(rows = GridAxis.Fill)
        change(delegate.state.value.homeLayout, paged)
        change(delegate.state.value.homeLayout, paged.copy(columns = GridAxis.Scroll, rows = GridAxis.Fixed(2)))

        coVerify(exactly = 1) {
            homeTileRepository.reflowScroll(
                OWNER,
                CustomGridLayout(CustomGridShape(3, 3), HomeScrollAxis.VERTICAL),
                CustomGridLayout(CustomGridShape(2, 2), HomeScrollAxis.HORIZONTAL)
            )
        }
    }

    @Test
    fun `leaving scroll mode keeps the arrangement the tiles fit`() = runTest {
        val vertical = CustomGridConfig(columns = GridAxis.Fixed(3), rows = GridAxis.Scroll)
        val settings = start(vertical)

        change(settings, vertical.copy(rows = GridAxis.Fill))

        assertEquals(
            ScrollArrangement(HomeScrollAxis.VERTICAL, 3),
            delegate.state.value.homeLayout.customGrid.scrollArrangement
        )
    }

    @Test
    fun `returning to the shape the tiles were arranged for does not reflow`() = runTest {
        val vertical = CustomGridConfig(columns = GridAxis.Fixed(3), rows = GridAxis.Scroll)
        start(vertical)

        change(delegate.state.value.homeLayout, vertical.copy(rows = GridAxis.Fill))
        change(delegate.state.value.homeLayout, vertical)

        coVerify(exactly = 0) { homeTileRepository.reflowScroll(any(), any(), any()) }
    }

    @Test
    fun `the first entry into scroll mode records its arrangement`() = runTest {
        val paged = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Fixed(3))
        val settings = start(paged)

        change(settings, paged.copy(columns = GridAxis.Scroll))

        assertEquals(
            ScrollArrangement(HomeScrollAxis.HORIZONTAL, 3),
            delegate.state.value.homeLayout.customGrid.scrollArrangement
        )
    }

    @Test
    fun `a change that leaves the shape alone does not reflow`() = runTest {
        val scrolling = CustomGridConfig(columns = GridAxis.Fixed(4), rows = GridAxis.Scroll)
        val settings = start(scrolling)

        change(settings, scrolling.copy(showEmptySlots = false))

        coVerify(exactly = 0) { homeTileRepository.reflowScroll(any(), any(), any()) }
    }
}
