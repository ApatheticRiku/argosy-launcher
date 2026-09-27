package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMAchievementService
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.domain.model.RA_FEATURED_MODE_WINDOW
import com.nendo.argosy.domain.model.RA_NEXT_LOCKED_CAP
import com.nendo.argosy.domain.model.RA_RECENT_UNLOCK_CAP
import com.nendo.argosy.domain.model.RaFeaturedMode
import com.nendo.argosy.domain.model.RaLockedAchievement
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.model.RaUnlock
import com.nendo.argosy.domain.model.featuredModeFor
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.parseTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RaTileContentRepository"

/**
 * How many of the games RomM reports as most recently awarded are re-read before a build, on top
 * of the tracked game. Bounds the network cost of one tile refresh.
 */
private const val RECENT_GAME_REFRESH_LIMIT = 5

/**
 * What the RetroAchievements home tile shows, in either of its modes.
 *
 * Server data comes from RomM's progression cache alone, refreshed at most once per app session.
 * The games it names as most recently awarded, plus the tracked game, are re-read through
 * [FetchAchievementsUseCase] when their local rows are missing or older than the award. Every
 * network step may fail; the content is always built from local rows afterwards, so an offline
 * device shows what it last knew and says nothing about the failure.
 */
@Singleton
class RaTileContentRepository @Inject constructor(
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao,
    private val romMAchievementService: RomMAchievementService,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    private val retroAchievementsRepository: RetroAchievementsRepository
) {

    /**
     * Null when no RetroAchievements account is signed in. [trackedGameId] null builds the account
     * overview; a game no longer in the library falls back to the overview rather than to nothing.
     */
    suspend fun load(trackedGameId: Long?): RaTileContent? = withContext(Dispatchers.IO) {
        val username = retroAchievementsRepository.getCredentials()?.username
            ?: return@withContext null
        val owner = retroAchievementsRepository.activeOwnerUserId()
        refreshLocalRows(trackedGameId, owner)
        val tracked = trackedGameId?.let { gameDao.getById(it) }
        if (tracked != null) trackedContent(username, owner, tracked) else accountContent(username, owner)
    }

    private suspend fun refreshLocalRows(trackedGameId: Long?, owner: Long) {
        val awards = try {
            romMAchievementService.refreshRAProgressionIfNeeded()
            latestAwards(romMAchievementService.getProgression())
        } catch (e: Exception) {
            Logger.warn(TAG, "Progression refresh failed, building from local rows: ${e.message}")
            return
        }
        val awardedAt = awards.toMap()
        val recentGames = awards
            .take(RECENT_GAME_REFRESH_LIMIT)
            .mapNotNull { (raId, _) -> gameDao.getByRaId(raId) }
        val tracked = trackedGameId?.let { gameDao.getById(it) }
        val candidates = (listOfNotNull(tracked) + recentGames).distinctBy { it.id }
        for (game in candidates) {
            val raId = game.effectiveRaId ?: continue
            if (!needsFetch(game, owner, awardedAt[raId])) continue
            try {
                fetchAchievementsUseCase(
                    gameId = game.id,
                    rommId = game.rommId,
                    raId = raId,
                    refreshProgression = false
                )
            } catch (e: Exception) {
                Logger.warn(TAG, "Achievement fetch failed for game ${game.id}: ${e.message}")
            }
        }
    }

    private fun latestAwards(progression: List<RomMRAGameProgression>): List<Pair<Long, Long>> =
        progression
            .mapNotNull { entry ->
                val raId = entry.romRaId ?: return@mapNotNull null
                val awardedAt = entry.mostRecentAwardedDate?.let(::parseTimestamp)
                    ?: return@mapNotNull null
                raId to awardedAt
            }
            .sortedByDescending { (_, awardedAt) -> awardedAt }
            .distinctBy { (raId, _) -> raId }

    private suspend fun needsFetch(game: GameEntity, owner: Long, awardedAt: Long?): Boolean {
        if (achievementDao.countByGameId(game.id, owner) == 0) return true
        val fetchedAt = game.achievementsFetchedAt ?: return true
        return awardedAt != null && fetchedAt < awardedAt
    }

    private suspend fun accountContent(username: String, owner: Long): RaTileContent.Account {
        val recent = achievementDao.getRecentUnlocks(owner, RA_FEATURED_MODE_WINDOW)
            .mapNotNull { it.toUnlock() }
        val mode = featuredModeFor(recent)
        val hardcoreOnly = mode == RaFeaturedMode.HARDCORE
        val latest = recent.firstOrNull()
        return RaTileContent.Account(
            username = username,
            featuredMode = mode,
            points = achievementDao.sumUnlockedPoints(owner, hardcoreOnly),
            unlocks = achievementDao.countUnlocked(owner, hardcoreOnly),
            latestUnlock = latest,
            recentUnlocks = recent.take(RA_RECENT_UNLOCK_CAP),
            latestGameEarned = latest?.let {
                achievementDao.countUnlockedByGameId(it.gameId, owner, hardcoreOnly)
            } ?: 0,
            latestGameTotal = latest?.let { achievementDao.countByGameId(it.gameId, owner) } ?: 0
        )
    }

    private suspend fun trackedContent(
        username: String,
        owner: Long,
        game: GameEntity
    ): RaTileContent.TrackedGame {
        val unlocked = achievementDao.getUnlockedForGame(game.id, owner).mapNotNull { it.toUnlock() }
        val mode = featuredModeFor(unlocked, window = unlocked.size)
        val hardcoreOnly = mode == RaFeaturedMode.HARDCORE
        val total = achievementDao.countByGameId(game.id, owner)
        val earned = achievementDao.countUnlockedByGameId(game.id, owner, hardcoreOnly)
        return RaTileContent.TrackedGame(
            username = username,
            featuredMode = mode,
            points = achievementDao.sumUnlockedPointsByGameId(game.id, owner, hardcoreOnly),
            unlocks = earned,
            latestUnlock = unlocked.firstOrNull(),
            gameId = game.id,
            gameTitle = game.title,
            gameCoverPath = game.displayCoverPath,
            total = total,
            nextLocked = achievementDao
                .getNextLocked(game.id, owner, hardcoreOnly, RA_NEXT_LOCKED_CAP)
                .map { it.toLocked() },
            mastered = total > 0 && earned == total
        )
    }
}

private fun AchievementDao.UnlockWithGameRow.toUnlock(): RaUnlock? {
    val unlockedAt = achievement.unlockedHardcoreAt ?: achievement.unlockedAt ?: return null
    return RaUnlock(
        raId = achievement.raId,
        title = achievement.title,
        description = achievement.description,
        points = achievement.points,
        badgePath = achievement.badgePath,
        unlockedAt = unlockedAt,
        hardcore = achievement.unlockedHardcoreAt != null,
        gameId = achievement.gameId,
        gameTitle = gameTitle,
        gameCoverPath = gameCoverPath
    )
}

private fun AchievementEntity.toLocked(): RaLockedAchievement = RaLockedAchievement(
    raId = raId,
    title = title,
    description = description,
    points = points,
    badgeLockPath = badgePath
)
