package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PushInstallUseCaseTest {

    private lateinit var romMRepository: RomMRepository
    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var platformDao: PlatformDao
    private lateinit var gameRepository: GameRepository
    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var downloadGameUseCase: DownloadGameUseCase
    private lateinit var useCase: PushInstallUseCase

    @Before
    fun setup() {
        romMRepository = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        emulatorDetector = mockk(relaxed = true)
        downloadGameUseCase = mockk(relaxed = true)
        useCase = PushInstallUseCase(
            romMRepository,
            gameDao,
            gameFileDao,
            platformDao,
            gameRepository,
            emulatorDetector,
            downloadGameUseCase
        )

        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Success(game())
        coEvery { gameRepository.validateAndDiscoverGame(GAME_ID) } returns false
        coEvery { gameRepository.getAvailableStorageBytes() } returns 1_000_000L
        every { emulatorDetector.installedEmulators } returns MutableStateFlow(emptyList())
        coEvery { emulatorDetector.detectEmulators() } returns emptyList()
        every { emulatorDetector.hasAnyEmulator("nes") } returns true
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns emptyList()
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.Queued
    }

    @Test
    fun `a game already on disk is already installed and nothing is queued`() = runTest {
        coEvery { gameRepository.validateAndDiscoverGame(GAME_ID) } returns true

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a game not on disk is queued`() = runTest {
        coEvery { gameRepository.validateAndDiscoverGame(GAME_ID) } returns false

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        coVerify(exactly = 1) { downloadGameUseCase(GAME_ID, null) }
    }

    @Test
    fun `a download that reports already downloaded is already installed`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.AlreadyDownloaded

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `every disc already downloaded is already installed`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns
            DownloadResult.Error(DownloadGameFailureReason.AllDiscsAlreadyDownloaded)

        assertEquals(PushInstallOutcome.AlreadyInstalled, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `a platform with no emulator fails without queueing`() = runTest {
        every { emulatorDetector.hasAnyEmulator("nes") } returns false

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR),
            useCase(ROM_ID, emptyList())
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a platform with an emulator passes the emulator check`() = runTest {
        every { emulatorDetector.hasAnyEmulator("nes") } returns true

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `emulators are detected once when none have been detected yet`() = runTest {
        useCase(ROM_ID, emptyList())

        coVerify(exactly = 1) { emulatorDetector.detectEmulators() }
    }

    @Test
    fun `an android game skips the emulator check`() = runTest {
        val android = game(platformId = LocalPlatformIds.ANDROID, platformSlug = "android")
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Success(android)
        every { emulatorDetector.hasAnyEmulator(any()) } returns false

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
        verify(exactly = 0) { emulatorDetector.hasAnyEmulator(any()) }
    }

    @Test
    fun `non install categories are dropped and the rest are queued`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME),
            file(rommFileId = 11L, category = VariantCategory.SOUNDTRACK),
            file(rommFileId = 12L, category = VariantCategory.SCREENSHOT),
            file(rommFileId = 13L, category = VariantCategory.CHEAT),
            file(rommFileId = 14L, category = VariantCategory.MANUAL),
            file(rommFileId = 15L, category = VariantCategory.WALKTHROUGH),
            file(rommFileId = 16L, category = VariantCategory.PATCH),
            file(rommFileId = 17L, category = VariantCategory.DLC)
        )

        val outcome = useCase(ROM_ID, listOf(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L))

        assertEquals(PushInstallOutcome.Queued, outcome)
        coVerify(exactly = 1) { downloadGameUseCase(GAME_ID, listOf(10L, 16L, 17L)) }
    }

    @Test
    fun `a request holding only non install categories fails`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME),
            file(rommFileId = 11L, category = VariantCategory.SOUNDTRACK),
            file(rommFileId = 14L, category = VariantCategory.MANUAL)
        )

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.NO_INSTALLABLE_FILES),
            useCase(ROM_ID, listOf(11L, 14L))
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `a rom the server does not know fails unresolved without a local fallback`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Error("not found", code = 404)
        coEvery { gameDao.getByRommId(ROM_ID) } returns game()

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED),
            useCase(ROM_ID, emptyList())
        )
        coVerify(exactly = 0) { gameDao.getByRommId(any()) }
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `an unreachable server with no stored rom fails unresolved`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Error("timeout", code = 503)
        coEvery { gameDao.getByRommId(ROM_ID) } returns null

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED),
            useCase(ROM_ID, emptyList())
        )
    }

    @Test
    fun `an unreachable server falls back to a stored rom that lists every requested file`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } throws IOException("offline")
        coEvery { gameDao.getByRommId(ROM_ID) } returns game()
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME)
        )

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, listOf(10L)))
        coVerify(exactly = 1) { downloadGameUseCase(GAME_ID, listOf(10L)) }
    }

    @Test
    fun `a stored rom missing a requested file is unresolved`() = runTest {
        coEvery { romMRepository.syncSingleRom(ROM_ID) } returns RomMResult.Error("timeout", code = 503)
        coEvery { gameDao.getByRommId(ROM_ID) } returns game()
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME)
        )

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED),
            useCase(ROM_ID, listOf(10L, 11L))
        )
    }

    @Test
    fun `less free space than the install needs fails for storage`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME, size = 1_000L)
        )
        coEvery { gameRepository.getAvailableStorageBytes() } returns 999L

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.INSUFFICIENT_STORAGE),
            useCase(ROM_ID, listOf(10L))
        )
        coVerify(exactly = 0) { downloadGameUseCase(any(), any()) }
    }

    @Test
    fun `free space equal to the install size is enough`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME, size = 1_000L)
        )
        coEvery { gameRepository.getAvailableStorageBytes() } returns 1_000L

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, listOf(10L)))
    }

    @Test
    fun `dropped categories do not count against free space`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME, size = 1_000L),
            file(rommFileId = 11L, category = VariantCategory.SOUNDTRACK, size = 50_000L)
        )
        coEvery { gameRepository.getAvailableStorageBytes() } returns 1_000L

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `an unreadable free space figure does not block the install`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME, size = 1_000L)
        )
        coEvery { gameRepository.getAvailableStorageBytes() } returns 0L

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, listOf(10L)))
    }

    @Test
    fun `the happy path syncs the rom, shows its platform, then queues`() = runTest {
        coEvery { gameFileDao.getFilesForGame(GAME_ID) } returns listOf(
            file(rommFileId = 10L, category = VariantCategory.GAME)
        )

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, listOf(10L)))
        coVerifyOrder {
            romMRepository.syncSingleRom(ROM_ID)
            platformDao.updateVisibility(PLATFORM_ID, true)
            downloadGameUseCase(GAME_ID, listOf(10L))
        }
    }

    @Test
    fun `a multi disc queue counts as queued`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns DownloadResult.MultiDiscQueued(2)

        assertEquals(PushInstallOutcome.Queued, useCase(ROM_ID, emptyList()))
    }

    @Test
    fun `a download error fails with its detail`() = runTest {
        coEvery { downloadGameUseCase(any(), any()) } returns
            DownloadResult.Error(DownloadGameFailureReason.GameNotSynced)

        assertEquals(
            PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, "game not synced"),
            useCase(ROM_ID, emptyList())
        )
    }

    private fun game(
        platformId: Long = PLATFORM_ID,
        platformSlug: String = "nes"
    ) = GameEntity(
        id = GAME_ID,
        platformId = platformId,
        platformSlug = platformSlug,
        title = "Test Game",
        sortTitle = "test game",
        localPath = null,
        rommId = ROM_ID,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun file(
        rommFileId: Long,
        category: VariantCategory,
        size: Long = 100L
    ) = GameFileEntity(
        id = rommFileId,
        gameId = GAME_ID,
        rommFileId = rommFileId,
        romId = ROM_ID,
        fileName = "file$rommFileId.bin",
        filePath = "roms/nes",
        category = category.key,
        fileSize = size
    )

    private companion object {
        const val ROM_ID = 456L
        const val GAME_ID = 123L
        const val PLATFORM_ID = 1L
    }
}
