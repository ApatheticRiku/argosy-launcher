package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncSaveOnSessionEndUseCaseTest {

    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorDetector = mockk<EmulatorDetector>(relaxed = true)
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)

    private val duckstation = EmulatorRegistry.getById("duckstation")!!
    private val retroarch = EmulatorRegistry.getById("retroarch")!!

    private val game = GameEntity(
        id = GAME_ID,
        title = "Ape Escape",
        sortTitle = "ape escape",
        platformId = PLATFORM_ID,
        platformSlug = "psx",
        rommId = 100L,
        igdbId = null,
        localPath = "/roms/psx/ape.chd",
        source = GameSource.ROMM_SYNCED
    )

    private val useCase = SyncSaveOnSessionEndUseCase(
        saveSyncRepository = saveSyncRepository,
        gameDao = gameDao,
        activeSaveRepository = mockk(relaxed = true),
        emulatorResolver = EmulatorResolver(
            emulatorDetector = emulatorDetector,
            emulatorConfigDao = emulatorConfigDao,
            userPreferencesRepository = preferencesRepository,
            libretroCoreMgr = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true) { every { isAppInstalled(any()) } returns false }
        ),
        preferencesRepository = preferencesRepository,
        romMRepository = romMRepository
    )

    @Before
    fun setUp() {
        every { preferencesRepository.userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { romMRepository.isConnected() } returns true
        coEvery { emulatorConfigDao.getByGameId(GAME_ID) } returns EmulatorConfigEntity(
            platformId = PLATFORM_ID,
            gameId = GAME_ID,
            packageName = duckstation.packageName,
            displayName = duckstation.displayName,
            coreName = null
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        every { emulatorDetector.installedEmulators } returns MutableStateFlow(
            listOf(installed(duckstation), installed(retroarch))
        )
        coEvery {
            saveSyncRepository.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
    }

    @Test
    fun `session end looks up saves under the emulator that actually ran`() = runTest {
        val result = useCase(GAME_ID, retroarch.packageName)

        assertEquals(SyncSaveOnSessionEndUseCase.Result.NoSaveFound, result)
        coVerify(exactly = 1) {
            saveSyncRepository.discoverSavePath(
                emulatorId = "retroarch",
                gameTitle = any(),
                platformSlug = any(),
                romPath = any(),
                cachedSaveId = any(),
                coreName = any(),
                emulatorPackage = retroarch.packageName,
                gameId = GAME_ID
            )
        }
        coVerify(exactly = 0) {
            saveSyncRepository.discoverSavePath("duckstation", any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `session end falls back to the launch resolver when the session recorded no package`() = runTest {
        useCase(GAME_ID, "")

        coVerify(exactly = 1) {
            saveSyncRepository.discoverSavePath("duckstation", any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `canSync answers for the emulator that actually ran`() = runTest {
        mockkObject(SavePathRegistry)
        try {
            every { SavePathRegistry.canSyncWithSettings("retroarch", true) } returns true
            every { SavePathRegistry.canSyncWithSettings("duckstation", true) } returns false

            assertTrue(useCase.canSync(GAME_ID, retroarch.packageName))
        } finally {
            unmockkObject(SavePathRegistry)
        }
    }

    private fun installed(def: EmulatorDef) =
        InstalledEmulator(def = def, versionName = "1.0", versionCode = 1L)

    private companion object {
        const val GAME_ID = 1L
        const val PLATFORM_ID = 10L
    }
}
