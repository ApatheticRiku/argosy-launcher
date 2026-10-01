package com.nendo.argosy.ui.startup

import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.domain.usecase.libretro.LibretroMigrationUseCase
import com.nendo.argosy.domain.usecase.libretro.MigrationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class StartupMaintenanceCoordinatorTest {

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var romMRepository: RomMRepository
    private lateinit var libretroMigrationUseCase: LibretroMigrationUseCase
    private lateinit var notificationManager: NotificationManager
    private lateinit var libraryPointerRepair: LibraryPointerRepair

    @Before
    fun setup() {
        preferencesRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        libretroMigrationUseCase = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        libraryPointerRepair = mockk(relaxed = true)
        every { preferencesRepository.userPreferences } returns flow { throw IllegalStateException("prefs unreadable") }
        every { romMRepository.isConnected() } returns true
        coEvery { libretroMigrationUseCase.runMigrationIfNeeded() } returns MigrationResult.AlreadyComplete
    }

    private fun coordinator() = StartupMaintenanceCoordinator(
        preferencesRepository,
        gameRepository,
        romMRepository,
        libretroMigrationUseCase,
        notificationManager,
        libraryPointerRepair
    )

    @Test
    fun `a throwing step does not stop the steps after it or the pointer repair`() = runBlocking {
        coEvery { romMRepository.syncCollections() } throws IllegalStateException("server down")

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(exactly = 1) { libretroMigrationUseCase.cleanupRemovedCores() }
        verify(exactly = 1) { libraryPointerRepair.start() }
    }

    @Test
    fun `a throwing last step still starts the pointer repair`() = runBlocking {
        coEvery { libretroMigrationUseCase.cleanupRemovedCores() } throws IllegalStateException("cores dir gone")

        withTimeout(PASS_TIMEOUT_MS) { coordinator().awaitPass() }

        coVerify(exactly = 1) { romMRepository.syncCollections() }
        verify(exactly = 1) { libraryPointerRepair.start() }
    }

    @Test
    fun `a second caller joins the completed pass instead of rethrowing a failure`() = runBlocking {
        coEvery { romMRepository.syncCollections() } throws IllegalStateException("server down")
        val coordinator = coordinator()

        withTimeout(PASS_TIMEOUT_MS) {
            coordinator.awaitPass()
            coordinator.awaitPass()
        }

        coVerify(exactly = 1) { romMRepository.syncCollections() }
        verify(exactly = 1) { libraryPointerRepair.start() }
    }

    private companion object {
        const val PASS_TIMEOUT_MS = 5_000L
    }
}
