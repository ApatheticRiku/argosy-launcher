package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val GAME_BOY_ID = 3L
private const val SNES_NAME = "Super Nintendo"
private const val GAME_BOY_NAME = "Game Boy"

/**
 * A library link and the configured library defaults arrive on separate coroutines. The defaults
 * pass rewrites the platform filter wholesale, so whichever lands second decides what the user
 * sees.
 */
class LibraryTileFilterArrivalTest {

    private val dispatcher = StandardTestDispatcher()
    private val platformRepository = mockk<PlatformRepository>(relaxed = true)
    private val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(
        relaxed = true
    )
    private val gameRepository = mockk<com.nendo.argosy.data.repository.GameRepository>(
        relaxed = true
    )
    private val mediaRepository = mockk<com.nendo.argosy.data.repository.MediaRepository>(
        relaxed = true
    )
    private val gameLaunchDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true)
    private val collectionModalDelegate =
        mockk<com.nendo.argosy.ui.screens.common.CollectionModalDelegate>(relaxed = true)
    private val gradientExtractionDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GradientExtractionDelegate>(relaxed = true)
    private val modalResetSignal = com.nendo.argosy.ui.ModalResetSignal()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameLaunchDelegate.syncOverlayState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.discPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.variantPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.memcardPickerState } returns MutableStateFlow(null)
        every { collectionModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
        )
        every { gradientExtractionDelegate.gradients } returns MutableStateFlow(emptyMap())
        every { gameRepository.observeHiddenList() } returns flowOf(emptyList())
        every { gameRepository.observeAllList() } returns flowOf(emptyList())
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(
            listOf(
                platform(GAME_BOY_ID, GAME_BOY_NAME),
                platform(9L, SNES_NAME)
            )
        )
        every { preferences.userPreferences } returns flowOf(
            UserPreferences(libraryDefaultPlatform = SNES_NAME)
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a tile's platforms outlive the configured library default`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setInitialTileFilters(LibraryLinkFilters(platformIds = setOf(GAME_BOY_ID)))
        advanceUntilIdle()

        assertEquals(setOf(GAME_BOY_ID), viewModel.platformIds())
    }

    /**
     * The defaults pass writes sort and source as well as platforms, so a link that set any of
     * them races it the same way the platform filter did.
     */
    @Test
    fun `a tile's sort and source outlive the configured library defaults`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setInitialTileFilters(
            LibraryLinkFilters(
                source = SourceFilter.FAVORITES,
                sort = ActiveSort(SortOption.RELEASE_YEAR, descending = false)
            )
        )
        advanceUntilIdle()

        val filters = viewModel.uiState.value.activeFilters
        assertEquals(SourceFilter.FAVORITES, filters.source)
        assertEquals(SortOption.RELEASE_YEAR, filters.sort.option)
        assertEquals(false, filters.sort.descending)
    }

    @Test
    fun `a tile's series and players reach the library`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setInitialTileFilters(
            LibraryLinkFilters(
                series = setOf("Mega Man"),
                players = PlayerCountBucket.FOUR_PLUS
            )
        )
        advanceUntilIdle()

        val filters = viewModel.uiState.value.activeFilters
        assertEquals(setOf("Mega Man"), filters.series)
        assertEquals(PlayerCountBucket.FOUR_PLUS, filters.players)
    }

    @Test
    fun `the configured default still applies when no tile asked for anything`() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(setOf(SNES_NAME), viewModel.platformLabels())
    }

    @Test
    fun `a tile arriving before the platforms load is applied once they do`() = runTest(dispatcher) {
        val platforms = MutableStateFlow(emptyList<PlatformEntity>())
        every { platformRepository.observeVisiblePlatforms() } returns platforms
        val viewModel = viewModel()

        viewModel.setInitialTileFilters(LibraryLinkFilters(platformIds = setOf(GAME_BOY_ID)))
        advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.platformIds())

        platforms.value = listOf(platform(GAME_BOY_ID, GAME_BOY_NAME))
        advanceUntilIdle()

        assertEquals(setOf(GAME_BOY_ID), viewModel.platformIds())
    }

    private fun viewModel() = LibraryViewModel(
        context = mockk(relaxed = true),
        platformRepository = platformRepository,
        gameRepository = gameRepository,
        mediaRepository = mediaRepository,
        collectionRepository = mockk(relaxed = true),
        gameNavigationContext = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        preferencesRepository = preferences,
        homeTileRepository = mockk(relaxed = true),
        customGridShapeStore = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameLaunchDelegate = gameLaunchDelegate,
        collectionModalDelegate = collectionModalDelegate,
        romMRepository = mockk(relaxed = true),
        playStoreService = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        apkInstallManager = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        modalResetSignal = modalResetSignal,
        gradientExtractionDelegate = gradientExtractionDelegate,
        downloadIndicatorSource = mockk(relaxed = true),
        emulatorDetector = mockk(relaxed = true),
        steamContentManager = mockk(relaxed = true),
        steamDownloadPromptController = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        emulatorLaunchTargetResolver = mockk(relaxed = true)
    )

    private fun LibraryViewModel.platformIds(): Set<Long> =
        uiState.value.activeFilters.platforms.map { it.id }.toSet()

    private fun LibraryViewModel.platformLabels(): Set<String> =
        uiState.value.activeFilters.platforms.map { it.label }.toSet()

    private fun platform(id: Long, name: String) = PlatformEntity(
        id = id,
        slug = name.lowercase().replace(' ', '-'),
        name = name,
        shortName = name,
        romExtensions = "zip"
    )
}
