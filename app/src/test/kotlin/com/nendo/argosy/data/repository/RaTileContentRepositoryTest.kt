package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.ra.RACredentials
import com.nendo.argosy.data.remote.romm.RomMAchievementService
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.RA_NEXT_LOCKED_CAP
import com.nendo.argosy.domain.model.RA_RECENT_UNLOCK_CAP
import com.nendo.argosy.domain.model.RaFeaturedMode
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.util.parseTimestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val OWNER = 3L
private const val TRACKED_GAME_ID = 5L
private const val TRACKED_RA_ID = 500L

class RaTileContentRepositoryTest {

    private val achievementDao = mockk<AchievementDao>()
    private val gameDao = mockk<GameDao>()
    private val service = mockk<RomMAchievementService>()
    private val fetch = mockk<FetchAchievementsUseCase>()
    private val raRepository = mockk<RetroAchievementsRepository>()
    private val gameArtDao = mockk<GameArtDao>()

    private val repository = RaTileContentRepository(
        achievementDao = achievementDao,
        gameDao = gameDao,
        romMAchievementService = service,
        fetchAchievementsUseCase = fetch,
        retroAchievementsRepository = raRepository,
        gameArtDao = gameArtDao
    )

    @Before
    fun setUp() {
        coEvery { raRepository.getCredentials() } returns RACredentials("nendo", "token")
        coEvery { raRepository.activeOwnerUserId() } returns OWNER
        coEvery { service.refreshRAProgressionIfNeeded(any()) } returns RomMResult.Error("offline")
        every { service.getProgression() } returns emptyList()
        coEvery { gameDao.getById(any()) } returns null
        coEvery { gameDao.getByRaId(any()) } returns null
        coEvery { fetch(any(), any(), any(), any()) } returns null
        coEvery { achievementDao.getRecentUnlocks(OWNER, any()) } returns emptyList()
        coEvery { achievementDao.getUnlockedForGame(any(), OWNER) } returns emptyList()
        coEvery { achievementDao.sumUnlockedPoints(OWNER, any()) } returns 0
        coEvery { achievementDao.countUnlocked(OWNER, any()) } returns 0
        coEvery { achievementDao.countByGameId(any(), OWNER) } returns 0
        coEvery { achievementDao.countUnlockedByGameId(any(), OWNER, any()) } returns 0
        coEvery { achievementDao.sumUnlockedPointsByGameId(any(), OWNER, any()) } returns 0
        coEvery { achievementDao.getNextLocked(any(), OWNER, any(), any()) } returns emptyList()
        coEvery { gameArtDao.getForGame(any()) } answers {
            val id = firstArg<Long>()
            listOf(GameArtEntity(id, ArtSlot.COVER.name, cachedPath = "/covers/$id.png"))
        }
    }

    private fun achievement(
        raId: Long,
        points: Int = 10,
        unlockedAt: Long? = null,
        unlockedHardcoreAt: Long? = null,
        gameId: Long = TRACKED_GAME_ID
    ) = AchievementEntity(
        id = raId,
        gameId = gameId,
        raId = raId,
        title = "Achievement $raId",
        description = "Do the thing",
        points = points,
        type = null,
        badgeUrl = "https://badge/$raId.png",
        badgeUrlLock = "https://badge/${raId}_lock.png",
        cachedBadgeUrl = if (unlockedAt != null || unlockedHardcoreAt != null) "/cache/$raId.png" else null,
        unlockedAt = unlockedAt,
        unlockedHardcoreAt = unlockedHardcoreAt,
        ownerUserId = OWNER
    )

    private fun unlockRow(
        raId: Long,
        unlockedAt: Long? = null,
        unlockedHardcoreAt: Long? = null,
        gameId: Long = 1L
    ) = AchievementDao.UnlockWithGameRow(
        achievement = achievement(
            raId = raId,
            unlockedAt = unlockedAt,
            unlockedHardcoreAt = unlockedHardcoreAt,
            gameId = gameId
        ),
        gameTitle = "Game $gameId",
        gameCoverPath = "/covers/$gameId.png"
    )

    private fun game(id: Long, raId: Long?, fetchedAt: Long? = null) = GameEntity(
        id = id,
        platformId = 1L,
        title = "Game $id",
        sortTitle = "game $id",
        localPath = null,
        rommId = 100L + id,
        igdbId = null,
        raId = raId,
        source = GameSource.ROMM_REMOTE,
        achievementsFetchedAt = fetchedAt
    )

    private fun softcoreWindow(hardcoreAtIndex: Int = -1): List<AchievementDao.UnlockWithGameRow> =
        (0 until 10).map { index ->
            val at = 1000L - index
            if (index == hardcoreAtIndex) {
                unlockRow(raId = index + 1L, unlockedHardcoreAt = at)
            } else {
                unlockRow(raId = index + 1L, unlockedAt = at)
            }
        }

    private fun awardDate(day: Int): String = "2025-01-%02dT00:00:00Z".format(day)

    @Test
    fun `signed out yields nothing`() = runTest {
        coEvery { raRepository.getCredentials() } returns null

        assertNull(repository.load(trackedGameId = null))
    }

    @Test
    fun `account content is built from local rows when the server is unreachable`() = runTest {
        coEvery { achievementDao.getRecentUnlocks(OWNER, any()) } returns softcoreWindow(hardcoreAtIndex = 3)
        coEvery { achievementDao.sumUnlockedPoints(OWNER, true) } returns 120
        coEvery { achievementDao.countUnlocked(OWNER, true) } returns 7
        coEvery { achievementDao.countUnlockedByGameId(1L, OWNER, true) } returns 3
        coEvery { achievementDao.countByGameId(1L, OWNER) } returns 12

        val content = repository.load(trackedGameId = null) as RaTileContent.Account

        assertEquals("nendo", content.username)
        assertEquals(RaFeaturedMode.HARDCORE, content.featuredMode)
        assertEquals(120, content.points)
        assertEquals(7, content.unlocks)
        assertEquals(1L, content.latestUnlock?.raId)
        assertEquals("/cache/1.png", content.latestUnlock?.badgePath)
        assertEquals("Game 1", content.latestUnlock?.gameTitle)
        assertEquals("/covers/1.png", content.latestUnlock?.gameCoverPath)
        assertFalse(content.latestUnlock?.hardcore ?: true)
        assertEquals(RA_RECENT_UNLOCK_CAP, content.recentUnlocks.size)
        assertEquals(1L, content.recentUnlocks.first().raId)
        assertTrue(content.recentUnlocks[3].hardcore)
        assertEquals(3, content.latestGameEarned)
        assertEquals(12, content.latestGameTotal)
        coVerify(exactly = 0) { fetch(any(), any(), any(), any()) }
    }

    @Test
    fun `an all softcore window tallies every unlock`() = runTest {
        coEvery { achievementDao.getRecentUnlocks(OWNER, any()) } returns softcoreWindow()

        val content = repository.load(trackedGameId = null) as RaTileContent.Account

        assertEquals(RaFeaturedMode.SOFTCORE, content.featuredMode)
        coVerify { achievementDao.sumUnlockedPoints(OWNER, false) }
        coVerify { achievementDao.countUnlocked(OWNER, false) }
    }

    @Test
    fun `a tracked game reports its own progress and mastery`() = runTest {
        coEvery { gameDao.getById(TRACKED_GAME_ID) } returns game(TRACKED_GAME_ID, TRACKED_RA_ID)
        coEvery { achievementDao.getUnlockedForGame(TRACKED_GAME_ID, OWNER) } returns listOf(
            unlockRow(raId = 2L, unlockedHardcoreAt = 900L, gameId = TRACKED_GAME_ID),
            unlockRow(raId = 1L, unlockedAt = 800L, gameId = TRACKED_GAME_ID)
        )
        coEvery { achievementDao.countByGameId(TRACKED_GAME_ID, OWNER) } returns 4
        coEvery { achievementDao.countUnlockedByGameId(TRACKED_GAME_ID, OWNER, true) } returns 4
        coEvery { achievementDao.sumUnlockedPointsByGameId(TRACKED_GAME_ID, OWNER, true) } returns 40
        coEvery { achievementDao.getNextLocked(TRACKED_GAME_ID, OWNER, true, any()) } returns
            listOf(11L, 12L, 13L, 14L).map { achievement(it, points = (it - 10).toInt() * 5) }

        val content = repository.load(TRACKED_GAME_ID) as RaTileContent.TrackedGame

        assertEquals(TRACKED_GAME_ID, content.gameId)
        assertEquals("Game 5", content.gameTitle)
        assertEquals("/covers/5.png", content.gameCoverPath)
        assertEquals(RaFeaturedMode.HARDCORE, content.featuredMode)
        assertEquals(4, content.total)
        assertEquals(4, content.unlocks)
        assertEquals(40, content.points)
        assertTrue(content.mastered)
        assertEquals(2L, content.latestUnlock?.raId)
        assertEquals(RA_NEXT_LOCKED_CAP, content.nextLocked.size)
        assertEquals(11L, content.nextLocked.first().raId)
        assertEquals("https://badge/11_lock.png", content.nextLocked.first().badgeLockPath)
        coVerify { achievementDao.getNextLocked(TRACKED_GAME_ID, OWNER, true, RA_NEXT_LOCKED_CAP) }
    }

    @Test
    fun `a tracked game short of its total is not mastered`() = runTest {
        coEvery { gameDao.getById(TRACKED_GAME_ID) } returns game(TRACKED_GAME_ID, TRACKED_RA_ID)
        coEvery { achievementDao.getUnlockedForGame(TRACKED_GAME_ID, OWNER) } returns listOf(
            unlockRow(raId = 1L, unlockedAt = 800L, gameId = TRACKED_GAME_ID)
        )
        coEvery { achievementDao.countByGameId(TRACKED_GAME_ID, OWNER) } returns 4
        coEvery { achievementDao.countUnlockedByGameId(TRACKED_GAME_ID, OWNER, false) } returns 2

        val content = repository.load(TRACKED_GAME_ID) as RaTileContent.TrackedGame

        assertEquals(RaFeaturedMode.SOFTCORE, content.featuredMode)
        assertEquals(2, content.unlocks)
        assertFalse(content.mastered)
    }

    @Test
    fun `a tracked game that left the library falls back to the account`() = runTest {
        coEvery { gameDao.getById(TRACKED_GAME_ID) } returns null

        assertTrue(repository.load(TRACKED_GAME_ID) is RaTileContent.Account)
    }

    @Test
    fun `refresh re-reads the tracked game and the five most recent awards only`() = runTest {
        coEvery { service.refreshRAProgressionIfNeeded(any()) } returns RomMResult.Success(Unit)
        every { service.getProgression() } returns (1..7).map { day ->
            RomMRAGameProgression(romRaId = 100L + day, mostRecentAwardedDate = awardDate(day))
        }
        (1..7).forEach { day ->
            coEvery { gameDao.getByRaId(100L + day) } returns game(100L + day, raId = 100L + day)
        }
        coEvery { gameDao.getById(TRACKED_GAME_ID) } returns game(TRACKED_GAME_ID, TRACKED_RA_ID)

        repository.load(TRACKED_GAME_ID)

        coVerify(exactly = 6) { fetch(any(), any(), any(), false) }
        coVerify { fetch(TRACKED_GAME_ID, 105L, TRACKED_RA_ID, false) }
        listOf(107L, 106L, 105L, 104L, 103L).forEach { id ->
            coVerify { fetch(id, 100L + id, id, false) }
        }
        coVerify(exactly = 0) { fetch(102L, any(), any(), any()) }
        coVerify(exactly = 0) { fetch(101L, any(), any(), any()) }
    }

    @Test
    fun `rows newer than the award are not fetched again`() = runTest {
        val award = awardDate(7)
        coEvery { service.refreshRAProgressionIfNeeded(any()) } returns RomMResult.Success(Unit)
        every { service.getProgression() } returns listOf(
            RomMRAGameProgression(romRaId = 107L, mostRecentAwardedDate = award)
        )
        coEvery { gameDao.getByRaId(107L) } returns
            game(107L, raId = 107L, fetchedAt = parseTimestamp(award)!! + 1L)
        coEvery { achievementDao.countByGameId(107L, OWNER) } returns 20

        repository.load(trackedGameId = null)

        coVerify(exactly = 0) { fetch(any(), any(), any(), any()) }
    }

    @Test
    fun `rows older than the award are fetched again`() = runTest {
        val award = awardDate(7)
        coEvery { service.refreshRAProgressionIfNeeded(any()) } returns RomMResult.Success(Unit)
        every { service.getProgression() } returns listOf(
            RomMRAGameProgression(romRaId = 107L, mostRecentAwardedDate = award)
        )
        coEvery { gameDao.getByRaId(107L) } returns
            game(107L, raId = 107L, fetchedAt = parseTimestamp(award)!! - 1L)
        coEvery { achievementDao.countByGameId(107L, OWNER) } returns 20

        repository.load(trackedGameId = null)

        coVerify(exactly = 1) { fetch(107L, 207L, 107L, false) }
    }

    @Test
    fun `a refresh that throws still builds from local rows`() = runTest {
        coEvery { service.refreshRAProgressionIfNeeded(any()) } throws RuntimeException("boom")
        coEvery { achievementDao.getRecentUnlocks(OWNER, any()) } returns softcoreWindow()

        val content = repository.load(trackedGameId = null)

        assertTrue(content is RaTileContent.Account)
        assertEquals(1L, content?.latestUnlock?.raId)
        coVerify(exactly = 0) { fetch(any(), any(), any(), any()) }
    }
}
