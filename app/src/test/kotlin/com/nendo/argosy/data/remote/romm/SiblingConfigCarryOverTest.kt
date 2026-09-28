package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameSiblingRow
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SiblingConfigCarryOverTest {

    private val gameDao: GameDao = mockk(relaxed = true)
    private val emulatorConfigDao: EmulatorConfigDao = mockk(relaxed = true)
    private val syncPreferences: SyncPreferencesRepository = mockk(relaxed = true)
    private val carryOver = SiblingConfigCarryOver(gameDao, emulatorConfigDao, syncPreferences)

    private val winnerConfig = EmulatorConfigEntity(
        id = 11L,
        platformId = 5L,
        gameId = 1L,
        packageName = "com.retroarch",
        displayName = "RetroArch",
        coreName = "gambatte",
        savePath = "/storage/saves/blue"
    )

    @Before
    fun setup() {
        coEvery { syncPreferences.isSiblingConfigCarryOverDone() } returns false
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns true
        coEvery { emulatorConfigDao.getGameOverrides() } returns listOf(winnerConfig)
        coEvery { emulatorConfigDao.getByGameId(any()) } returns null
        coEvery { emulatorConfigDao.getByGameId(1L) } returns winnerConfig
        coEvery { gameDao.getSiblingRows() } returns listOf(
            member(1L, GROUP),
            member(2L, GROUP),
            member(3L, GROUP)
        )
    }

    @Test
    fun `the winner's config is copied to regional copies that have none`() = runBlocking {
        carryOver.runOnce()

        coVerify(exactly = 1) { emulatorConfigDao.insert(winnerConfig.copy(id = 0, gameId = 2L)) }
        coVerify(exactly = 1) { emulatorConfigDao.insert(winnerConfig.copy(id = 0, gameId = 3L)) }
        coVerify(exactly = 0) { emulatorConfigDao.insert(match { it.gameId == 1L }) }
        coVerify { syncPreferences.setSiblingConfigCarryOverDone() }
    }

    @Test
    fun `a copy that already has a config keeps it`() = runBlocking {
        coEvery { emulatorConfigDao.getByGameId(3L) } returns
            winnerConfig.copy(id = 12L, gameId = 3L, packageName = "org.ppsspp.ppsspp")

        val copied = carryOver.carryOver()

        assertEquals(1, copied)
        coVerify(exactly = 0) { emulatorConfigDao.insert(match { it.gameId == 3L }) }
    }

    @Test
    fun `a group with two configured members is left alone`() = runBlocking {
        coEvery { emulatorConfigDao.getGameOverrides() } returns
            listOf(winnerConfig, winnerConfig.copy(id = 12L, gameId = 2L))

        val copied = carryOver.carryOver()

        assertEquals(0, copied)
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `members of another group and patched members get nothing`() = runBlocking {
        coEvery { gameDao.getSiblingRows() } returns listOf(
            member(1L, GROUP),
            member(4L, GROUP, isHack = true),
            member(5L, "igdb-5-9999")
        )

        val copied = carryOver.carryOver()

        assertEquals(0, copied)
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `the copy runs only once`() = runBlocking {
        carryOver.runOnce()
        coEvery { syncPreferences.isSiblingConfigCarryOverDone() } returns true
        carryOver.runOnce()

        coVerify(exactly = 1) { gameDao.getSiblingRows() }
        coVerify(exactly = 2) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `the copy waits for the split repair`() = runBlocking {
        coEvery { syncPreferences.isSiblingSplitRepairDone() } returns false

        carryOver.runOnce()

        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
        coVerify(exactly = 0) { syncPreferences.setSiblingConfigCarryOverDone() }
    }

    private fun member(id: Long, groupKey: String, isHack: Boolean = false) = GameSiblingRow(
        id = id,
        siblingGroupKey = groupKey,
        isHackVariant = isHack,
        isTranslationVariant = false,
        rommMainSibling = false,
        rommFileName = "Blue $id.gb",
        regions = null,
        localPath = null,
        isGroupVisible = true
    )

    private companion object {
        const val GROUP = "igdb-5-1511"
    }
}
