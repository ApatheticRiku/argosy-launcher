package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.GameUserOverlayDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SiblingSplitRepairTest {

    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var syncPreferences: SyncPreferencesRepository
    private lateinit var carryOver: VariantSaveCarryOver
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var stateCacheDao: StateCacheDao
    private lateinit var overlayDao: GameUserOverlayDao
    private lateinit var playSessionDao: PlaySessionDao
    private lateinit var repair: SiblingSplitRepair

    private val mergedAddedAt = Instant.parse("2026-01-01T00:00:00Z")

    private val merged = game(
        id = 1L,
        rommId = 100L,
        localPath = "/roms/gb/Blue (Germany).gb",
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        regions = "USA"
    )
    private val sibling = game(id = 2L, rommId = 200L, localPath = null, regions = "Germany, Europe")

    private val mergedOwnFile = file(id = 10L, gameId = 1L, romId = 100L, localPath = "/roms/gb/Blue (USA).gb")
    private val movedFile = file(id = 20L, gameId = 2L, romId = 200L, localPath = "/roms/gb/Blue (Germany).gb")

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        saveSyncDao = mockk(relaxed = true)
        syncPreferences = mockk(relaxed = true)
        carryOver = mockk(relaxed = true)
        saveCacheDao = mockk(relaxed = true)
        stateCacheDao = mockk(relaxed = true)
        overlayDao = mockk(relaxed = true)
        playSessionDao = mockk(relaxed = true)
        repair = SiblingSplitRepair(
            gameDao, gameFileDao, saveSyncDao, saveCacheDao, stateCacheDao, overlayDao, playSessionDao,
            syncPreferences, carryOver
        )
        coEvery { saveSyncDao.countForGame(any()) } returns 0

        coEvery { saveCacheDao.getRowsWithChannel() } returns emptyList()
        coEvery { saveCacheDao.moveToGame(any(), any(), any()) } returns 1
        coEvery { stateCacheDao.getRowsWithChannel() } returns emptyList()
        coEvery { stateCacheDao.moveToGame(any(), any(), any()) } returns 1
        coEvery { gameDao.getAllByIgdbIdAndPlatform(any(), any()) } returns emptyList()

        coEvery { gameDao.getGamesWithLocalPath() } returns emptyList()
        coEvery { gameDao.getGamesWithFileSelection() } returns emptyList()
        coEvery { gameDao.getById(any()) } returns null
        coEvery { gameDao.getByRommId(any()) } returns null
        coEvery { gameFileDao.getAllWithLocalPath() } returns emptyList()
        coEvery { gameFileDao.getFilesForGame(any()) } returns emptyList()
        coEvery { gameFileDao.getVersionGroupedFiles() } returns emptyList()
        coEvery { gameFileDao.getById(any()) } returns null
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns emptyList()
        coEvery { saveSyncDao.getByGameEmulatorAndNullChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { saveSyncDao.moveToGame(any(), any(), any()) } returns 1
        coEvery { carryOver.carryOver(any(), any(), any()) } returns 0
    }

    @Test
    fun `a merged game pointing at a sibling file hands the path over and falls back to its own file`() = runBlocking {
        coEvery { gameDao.getGamesWithLocalPath() } returns listOf(merged)
        coEvery { gameFileDao.getAllWithLocalPath() } returns listOf(mergedOwnFile, movedFile)
        coEvery { gameDao.getById(2L) } returns sibling
        coEvery { gameFileDao.getFilesForGame(1L) } returns listOf(mergedOwnFile)

        val outcome = repair.repair()

        assertEquals(1, outcome.pathsHandedOver)
        coVerify {
            gameDao.updateLocalPath(
                2L, "/roms/gb/Blue (Germany).gb", GameSource.ROMM_SYNCED, FileOrigin.ROMM_DOWNLOAD, any()
            )
        }
        coVerify {
            gameDao.updateLocalPath(
                1L, "/roms/gb/Blue (USA).gb", merged.source, FileOrigin.ROMM_DOWNLOAD, mergedAddedAt
            )
        }
        coVerify(exactly = 0) { gameDao.clearLocalPath(any()) }
    }

    @Test
    fun `a merged game with no downloaded file of its own loses its path`() = runBlocking {
        coEvery { gameDao.getGamesWithLocalPath() } returns listOf(merged)
        coEvery { gameFileDao.getAllWithLocalPath() } returns listOf(movedFile)
        coEvery { gameDao.getById(2L) } returns sibling
        coEvery { gameFileDao.getFilesForGame(1L) } returns listOf(mergedOwnFile.copy(localPath = null))

        repair.repair()

        coVerify { gameDao.clearLocalPath(1L) }
        coVerify(exactly = 0) { gameDao.updateLocalPath(1L, any(), any(), any(), any()) }
    }

    @Test
    fun `a sibling that already has its own path keeps it`() = runBlocking {
        coEvery { gameDao.getGamesWithLocalPath() } returns listOf(merged)
        coEvery { gameFileDao.getAllWithLocalPath() } returns listOf(movedFile)
        coEvery { gameDao.getById(2L) } returns sibling.copy(localPath = "/roms/gb/Blue (Europe).gb")

        repair.repair()

        coVerify(exactly = 0) { gameDao.updateLocalPath(2L, any(), any(), any(), any()) }
        coVerify { gameDao.clearLocalPath(1L) }
    }

    @Test
    fun `one file claimed by two games stays with the older game and the split-off game releases it`() = runBlocking {
        val shared = "/roms/nes/720 Degrees (USA)/720 Degrees (USA).nes"
        val keeperRow = file(id = 10L, gameId = 1L, romId = 100L, localPath = shared)
        val splitRow = file(id = 20L, gameId = 2L, romId = 200L, localPath = shared)
        coEvery { gameFileDao.getAllWithLocalPath() } returns listOf(keeperRow, splitRow)
        coEvery { gameDao.getById(2L) } returns sibling.copy(localPath = shared)

        repair.repair()

        coVerify { gameFileDao.clearLocalPath(20L) }
        coVerify(exactly = 0) { gameFileDao.clearLocalPath(10L) }
        coVerify { gameDao.clearLocalPath(2L) }
        coVerify(exactly = 0) { gameDao.clearLocalPath(1L) }
    }

    @Test
    fun `an adopted file claimed by two games stays with the game whose rom file has its name`() = runBlocking {
        val shared = "/roms/3do/Bust-A-Move/Bust-A-Move (1995)(Taito)(US).cue"
        coEvery { gameDao.getGamesWithLocalPath() } returns
            listOf(game(id = 1L, rommId = 100L, localPath = shared), game(id = 2L, rommId = 200L, localPath = shared))
        coEvery { gameFileDao.getFilesForGame(1L) } returns
            listOf(file(id = 10L, gameId = 1L, romId = 100L, localPath = null).copy(fileName = "Puzzle Bobble (JP).zip"))
        coEvery { gameFileDao.getFilesForGame(2L) } returns
            listOf(file(id = 20L, gameId = 2L, romId = 200L, localPath = null).copy(fileName = "Bust-A-Move (1995)(Taito)(US).zip"))

        val outcome = repair.repair()

        assertEquals(1, outcome.pathsHandedOver)
        coVerify { gameDao.clearLocalPath(1L) }
        coVerify(exactly = 0) { gameDao.clearLocalPath(2L) }
    }

    @Test
    fun `an adopted file no claimant's rom names stays with the older game`() = runBlocking {
        val shared = "/roms/3do/Trip'd/Trip'd (EU-US).iso"
        coEvery { gameDao.getGamesWithLocalPath() } returns
            listOf(game(id = 2L, rommId = 200L, localPath = shared), game(id = 1L, rommId = 100L, localPath = shared))

        repair.repair()

        coVerify { gameDao.clearLocalPath(2L) }
        coVerify(exactly = 0) { gameDao.clearLocalPath(1L) }
    }

    @Test
    fun `a game pointing at its own file is left alone`() = runBlocking {
        coEvery { gameDao.getGamesWithLocalPath() } returns
            listOf(merged.copy(localPath = "/roms/gb/Blue (USA).gb"))
        coEvery { gameFileDao.getAllWithLocalPath() } returns listOf(mergedOwnFile, movedFile)

        val outcome = repair.repair()

        assertEquals(0, outcome.pathsHandedOver)
        coVerify(exactly = 0) { gameDao.updateLocalPath(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { gameDao.clearLocalPath(any()) }
    }

    @Test
    fun `file selections naming another game's row are cleared and own ones kept`() = runBlocking {
        coEvery { gameDao.getGamesWithFileSelection() } returns
            listOf(merged.copy(activeVariantFileId = 20L, lastPlayedFileId = 10L))
        coEvery { gameFileDao.getById(20L) } returns movedFile
        coEvery { gameFileDao.getById(10L) } returns mergedOwnFile

        val outcome = repair.repair()

        assertEquals(1, outcome.selectionsCleared)
        coVerify { gameDao.updateActiveVariantFileId(1L, null) }
        coVerify(exactly = 0) { gameDao.updateLastPlayedFileId(any(), any()) }
    }

    @Test
    fun `a last played file owned by another game is cleared`() = runBlocking {
        coEvery { gameDao.getGamesWithFileSelection() } returns listOf(merged.copy(lastPlayedFileId = 20L))
        coEvery { gameFileDao.getById(20L) } returns movedFile

        repair.repair()

        coVerify { gameDao.updateLastPlayedFileId(1L, null) }
        coVerify(exactly = 0) { gameDao.updateActiveVariantFileId(any(), any()) }
    }

    @Test
    fun `a save sync row keyed to a sibling rom moves to that game with its prefix removed`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany/Slot 1"),
            saveSync(id = 6L, gameId = 1L, rommId = 200L, channel = "Germany")
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling

        val outcome = repair.repair()

        assertEquals(2, outcome.saveSyncRowsMoved)
        coVerify { saveSyncDao.moveToGame(5L, 2L, "Slot 1") }
        coVerify { saveSyncDao.moveToGame(6L, 2L, null) }
    }

    @Test
    fun `a save sync row stays put when the sibling already holds that channel`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.getByGameEmulatorAndNullChannel(2L, "argosy", 7L) } returns
            saveSync(id = 9L, gameId = 2L, rommId = 200L, channel = null)

        val outcome = repair.repair()

        assertEquals(0, outcome.saveSyncRowsMoved)
        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }
    }

    @Test
    fun `a save sync row whose rom has no game of its own is not touched`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 300L, channel = "Japan"))

        repair.repair()

        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }
    }

    @Test
    fun `cached saves and states under a regional copy's prefix move to that copy`() = runBlocking {
        coEvery { gameDao.getById(1L) } returns merged
        coEvery { gameDao.getAllByIgdbIdAndPlatform(1511L, 5L) } returns listOf(merged, sibling)
        coEvery { saveCacheDao.getRowsWithChannel() } returns listOf(
            saveCache(id = 30L, gameId = 1L, channel = "Germany/Slot 1"),
            saveCache(id = 31L, gameId = 1L, channel = "Slot 2")
        )
        coEvery { stateCacheDao.getRowsWithChannel() } returns listOf(stateCache(id = 40L, gameId = 1L, channel = "Germany"))

        val outcome = repair.repair()

        assertEquals(2, outcome.cacheRowsMoved)
        coVerify { saveCacheDao.moveToGame(30L, 2L, "Slot 1") }
        coVerify(exactly = 0) { saveCacheDao.moveToGame(31L, any(), any()) }
        coVerify { stateCacheDao.moveToGame(40L, 2L, null) }
    }

    @Test
    fun `a cached row two regional copies could claim stays on the merged game`() = runBlocking {
        val otherGerman = game(id = 3L, rommId = 300L, localPath = null, regions = "Germany")
        coEvery { gameDao.getById(1L) } returns merged
        coEvery { gameDao.getAllByIgdbIdAndPlatform(1511L, 5L) } returns listOf(merged, sibling, otherGerman)
        coEvery { saveCacheDao.getRowsWithChannel() } returns listOf(saveCache(id = 30L, gameId = 1L, channel = "Germany/Slot 1"))

        val outcome = repair.repair()

        assertEquals(0, outcome.cacheRowsMoved)
        coVerify(exactly = 0) { saveCacheDao.moveToGame(any(), any(), any()) }
    }

    @Test
    fun `a cached row follows the save sync owner when the game has no igdb id`() = runBlocking {
        coEvery { gameDao.getById(1L) } returns merged.copy(igdbId = null)
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { stateCacheDao.getRowsWithChannel() } returns listOf(stateCache(id = 40L, gameId = 1L, channel = "Germany/Boss"))

        repair.repair()

        coVerify { stateCacheDao.moveToGame(40L, 2L, "Boss") }
    }

    @Test
    fun `play history moves with saves that all go to one regional copy`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 1

        val outcome = repair.repair()

        assertEquals(1, outcome.historyMoved)
        coVerifyOrder {
            overlayDao.movePlayTotals(fromGameId = 1L, toGameId = 2L)
            playSessionDao.moveToGame(1L, 2L)
            saveSyncDao.moveToGame(5L, 2L, null)
        }
    }

    @Test
    fun `play history stays when the merged game keeps save rows of its own`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 2

        val outcome = repair.repair()

        assertEquals(0, outcome.historyMoved)
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
        coVerify(exactly = 0) { playSessionDao.moveToGame(any(), any()) }
        coVerify { saveSyncDao.moveToGame(5L, 2L, null) }
    }

    @Test
    fun `play history stays when saves went to two regional copies`() = runBlocking {
        val japan = game(id = 3L, rommId = 300L, localPath = null, regions = "Japan")
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"),
            saveSync(id = 6L, gameId = 1L, rommId = 300L, channel = "Japan")
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { gameDao.getByRommId(300L) } returns japan
        coEvery { saveSyncDao.countForGame(1L) } returns 2

        val outcome = repair.repair()

        assertEquals(0, outcome.historyMoved)
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
    }

    @Test
    fun `play history stays when a save row's destination is already taken`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 1
        coEvery { saveSyncDao.getByGameEmulatorAndNullChannel(2L, "argosy", 7L) } returns
            saveSync(id = 9L, gameId = 2L, rommId = 200L, channel = null)

        val outcome = repair.repair()

        assertEquals(0, outcome.historyMoved)
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
    }

    @Test
    fun `play history stays when two rows would land on the same slot`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"),
            saveSync(id = 6L, gameId = 1L, rommId = 200L, channel = null)
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 2

        val outcome = repair.repair()

        assertEquals(0, outcome.historyMoved)
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, null) }
        coVerify(exactly = 0) { saveSyncDao.moveToGame(6L, any(), any()) }
    }

    @Test
    fun `an unowned row and an owned row for the same slot keep history in place and move once`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Slot 1").copy(ownerUserId = null),
            saveSync(id = 6L, gameId = 1L, rommId = 200L, channel = "Germany/Slot 1")
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 2

        val outcome = repair.repair()

        assertEquals(0, outcome.historyMoved)
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, "Slot 1") }
        coVerify(exactly = 0) { saveSyncDao.moveToGame(6L, any(), any()) }
    }

    @Test
    fun `a slot an earlier merged game filled keeps the later game's history and row in place`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Slot 1"),
            saveSync(id = 6L, gameId = 4L, rommId = 200L, channel = "Slot 1")
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 1
        coEvery { saveSyncDao.countForGame(4L) } returns 1

        val outcome = repair.repair()

        assertEquals(1, outcome.historyMoved)
        coVerify(exactly = 1) { overlayDao.movePlayTotals(1L, 2L) }
        coVerify(exactly = 0) { overlayDao.movePlayTotals(4L, any()) }
        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, "Slot 1") }
        coVerify(exactly = 0) { saveSyncDao.moveToGame(6L, any(), any()) }
    }

    @Test
    fun `a history move that fails leaves the save rows for the retry, which moves both`() = runBlocking {
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.countForGame(1L) } returns 1
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false
        coEvery { overlayDao.movePlayTotals(1L, 2L) } throws IllegalStateException("interrupted") andThen Unit

        repair.runOnce()

        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }
        coVerify(exactly = 0) { syncPreferences.setSiblingSplitRepairDone() }

        repair.runOnce()

        coVerify(exactly = 2) { overlayDao.movePlayTotals(1L, 2L) }
        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, null) }
        coVerify { syncPreferences.setSiblingSplitRepairDone() }
    }

    @Test
    fun `a blocked save sync row moves on a later pass once its slot frees, not before`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.getByGameEmulatorAndNullChannel(2L, "argosy", 7L) } returns
            saveSync(id = 9L, gameId = 2L, rommId = 200L, channel = null) andThen null

        assertEquals(0, repair.retryBlockedSaveSyncMoves())
        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }

        assertEquals(1, repair.retryBlockedSaveSyncMoves())
        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, null) }
        coVerify(exactly = 0) { overlayDao.movePlayTotals(any(), any()) }
    }

    @Test
    fun `the blocked-row retry never writes over an occupied slot`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany/Slot 1"))
        coEvery { gameDao.getByRommId(200L) } returns sibling
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(2L, "argosy", "Slot 1", 7L) } returns
            saveSync(id = 9L, gameId = 2L, rommId = 200L, channel = "Slot 1")

        repeat(3) { assertEquals(0, repair.retryBlockedSaveSyncMoves()) }

        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }
    }

    @Test
    fun `two blocked rows for one freed slot move only the first`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns listOf(
            saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Slot 1"),
            saveSync(id = 6L, gameId = 4L, rommId = 200L, channel = "Slot 1")
        )
        coEvery { gameDao.getByRommId(200L) } returns sibling

        assertEquals(1, repair.retryBlockedSaveSyncMoves())

        coVerify(exactly = 1) { saveSyncDao.moveToGame(5L, 2L, "Slot 1") }
        coVerify(exactly = 0) { saveSyncDao.moveToGame(6L, any(), any()) }
    }

    @Test
    fun `the blocked-row retry waits for the one-time repair to finish`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false
        coEvery { saveSyncDao.getRowsKeyedToAnotherRom() } returns
            listOf(saveSync(id = 5L, gameId = 1L, rommId = 200L, channel = "Germany"))
        coEvery { gameDao.getByRommId(200L) } returns sibling

        assertEquals(0, repair.retryBlockedSaveSyncMoves())

        coVerify(exactly = 0) { saveSyncDao.getRowsKeyedToAnotherRom() }
        coVerify(exactly = 0) { saveSyncDao.moveToGame(any(), any(), any()) }
    }

    @Test
    fun `the channel prefix falls back to the rom id when the game has no region`() {
        val unregioned = sibling.copy(regions = null)

        assertNull(stripAbsorbedChannelPrefix("Version 200", unregioned))
        assertEquals("Slot 2", stripAbsorbedChannelPrefix("Version 200/Slot 2", unregioned))
    }

    @Test
    fun `a channel absorption never touched is returned unchanged`() {
        assertEquals("Slot 1", stripAbsorbedChannelPrefix("Slot 1", sibling))
        assertEquals("Germanyish", stripAbsorbedChannelPrefix("Germanyish", sibling))
        assertNull(stripAbsorbedChannelPrefix(null, sibling))
    }

    @Test
    fun `runOnce skips a device that already ran the repair`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true

        repair.runOnce()

        coVerify(exactly = 0) { gameDao.getGamesWithLocalPath() }
        coVerify(exactly = 0) { syncPreferences.setSiblingSplitRepairDone() }
    }

    @Test
    fun `runOnce records completion after a clean run`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false

        repair.runOnce()

        coVerify { syncPreferences.setSiblingSplitRepairDone() }
    }

    @Test
    fun `runOnce leaves the flag unset when the repair fails`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false
        coEvery { gameDao.getGamesWithLocalPath() } throws IllegalStateException("db closed")

        repair.runOnce()

        coVerify(exactly = 0) { syncPreferences.setSiblingSplitRepairDone() }
    }

    private fun game(
        id: Long,
        rommId: Long,
        localPath: String?,
        fileOrigin: FileOrigin = FileOrigin.ADOPTED,
        regions: String? = null
    ) = GameEntity(
        id = id,
        platformId = 5L,
        platformSlug = "gb",
        title = "Blue $id",
        sortTitle = "blue $id",
        localPath = localPath,
        fileOrigin = fileOrigin,
        rommId = rommId,
        igdbId = 1511L,
        source = if (localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
        regions = regions,
        addedAt = mergedAddedAt
    )

    private fun file(id: Long, gameId: Long, romId: Long, localPath: String?) = GameFileEntity(
        id = id,
        gameId = gameId,
        rommFileId = id + 1000L,
        romId = romId,
        fileName = localPath?.substringAfterLast('/') ?: "rom$id.gb",
        filePath = "gb/rom$romId",
        category = "game",
        fileSize = 1024L,
        localPath = localPath,
        isLaunchTarget = true,
        versionGroup = "romm:$romId"
    )

    private fun saveCache(id: Long, gameId: Long, channel: String?) = SaveCacheEntity(
        id = id,
        gameId = gameId,
        emulatorId = "argosy",
        cachedAt = mergedAddedAt,
        saveSize = 8192L,
        cachePath = "saves/$id.srm",
        channelName = channel
    )

    private fun stateCache(id: Long, gameId: Long, channel: String?) = StateCacheEntity(
        id = id,
        gameId = gameId,
        platformSlug = "gb",
        emulatorId = "argosy",
        slotNumber = 1,
        channelName = channel,
        cachedAt = mergedAddedAt,
        stateSize = 8192L,
        cachePath = "states/$id.state"
    )

    private fun saveSync(id: Long, gameId: Long, rommId: Long, channel: String?) = SaveSyncEntity(
        id = id,
        gameId = gameId,
        rommId = rommId,
        emulatorId = "argosy",
        channelName = channel,
        syncStatus = SaveSyncEntity.STATUS_SYNCED,
        ownerUserId = 7L
    )
}
