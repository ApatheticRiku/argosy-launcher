package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotActionResult
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelService
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncEngine
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val GAME_ID = 12L
private const val EMULATOR = "argosy"
private const val FILE_ID = 99L

class UseSnapshotChannelOnDeviceUseCaseTest {

    private val activeSaves: ActiveSaveRepository = mockk(relaxed = true)
    private val activateChannel: ActivateSaveChannelUseCase = mockk(relaxed = true)
    private val channelService: SnapshotChannelService = mockk(relaxed = true)
    private val engine: SnapshotSyncEngine = mockk(relaxed = true)
    private val useCase = UseSnapshotChannelOnDeviceUseCase(activeSaves, activateChannel, channelService, engine)

    private val speedrun = RomMChannel(id = "c-speedrun", label = "Speedrun", romFileId = FILE_ID)

    @Before
    fun setUp() {
        coEvery { engine.sync(GAME_ID, EMULATOR, any()) } returns SnapshotSyncResult.UpToDate
        every { channelService.argosyChannelOf(speedrun) } returns "Speedrun"
    }

    @Test
    fun `a picked snapshot is placed on this device and the channel's current is not`() = runTest {
        coEvery { activeSaves.getActiveChannel(GAME_ID) } returns null
        coEvery { engine.restoreLocally(GAME_ID, EMULATOR, "Speedrun", 37) } returns SnapshotSyncResult.Applied(37)

        assertEquals(SnapshotActionResult.Done, useCase(GAME_ID, EMULATOR, FILE_ID, speedrun, snapshotId = 37))

        coVerify { engine.restoreLocally(GAME_ID, EMULATOR, "Speedrun", 37) }
        coVerify(exactly = 0) { engine.keepServer(any(), any(), any()) }
    }

    @Test
    fun `without a picked snapshot the channel's current is placed`() = runTest {
        coEvery { activeSaves.getActiveChannel(GAME_ID) } returns null
        coEvery { engine.keepServer(GAME_ID, EMULATOR, "Speedrun") } returns SnapshotSyncResult.Applied(42)

        assertEquals(SnapshotActionResult.Done, useCase(GAME_ID, EMULATOR, FILE_ID, speedrun))

        coVerify(exactly = 0) { engine.restoreLocally(any(), any(), any(), any()) }
    }

    @Test
    fun `using the channel already active leaves activation alone`() = runTest {
        coEvery { activeSaves.getActiveChannel(GAME_ID) } returns "Speedrun"
        coEvery { engine.keepServer(GAME_ID, EMULATOR, "Speedrun") } returns SnapshotSyncResult.Applied(42)

        useCase(GAME_ID, EMULATOR, FILE_ID, speedrun)

        coVerify(exactly = 0) { activateChannel(any(), any()) }
        coVerify(exactly = 0) { channelService.rememberDeviceChannel(any(), any(), any(), any()) }
    }

    @Test
    fun `switching channels activates the new one and remembers it for this device`() = runTest {
        coEvery { activeSaves.getActiveChannel(GAME_ID) } returns null
        coEvery { engine.keepServer(GAME_ID, EMULATOR, "Speedrun") } returns SnapshotSyncResult.Applied(42)

        useCase(GAME_ID, EMULATOR, FILE_ID, speedrun)

        coVerify { activateChannel(GAME_ID, "Speedrun") }
        coVerify { channelService.rememberDeviceChannel(GAME_ID, "Speedrun", "c-speedrun", FILE_ID) }
    }
}
