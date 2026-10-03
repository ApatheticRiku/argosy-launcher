package com.nendo.argosy.data.remote.romm

import android.os.SystemClock
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMAchievementService"
private const val REFRESH_FLOOR_MS = 60_000L

@Singleton
class RomMAchievementService @Inject constructor(
    private val connectionManager: RomMConnectionManager
) {
    private val api: RomMApi? get() = connectionManager.getApi()

    private val refreshMutex = Mutex()
    @Volatile private var refreshAttemptedThisSession = false
    @Volatile private var lastRefreshAttemptAt = 0L
    @Volatile private var cachedRAProgression: Map<Long, List<RomMEarnedAchievement>> = emptyMap()
    @Volatile private var cachedProgression: List<RomMRAGameProgression> = emptyList()

    fun onAppResumed() {
        refreshAttemptedThisSession = false
    }

    fun onAccountChanged() {
        refreshAttemptedThisSession = false
        lastRefreshAttemptAt = 0L
        cachedRAProgression = emptyMap()
        cachedProgression = emptyList()
    }

    fun getEarnedBadgeIds(raGameId: Long): Set<String> {
        return cachedRAProgression[raGameId]?.map { it.id }?.toSet() ?: emptySet()
    }

    fun getEarnedAchievements(raGameId: Long): List<RomMEarnedAchievement> {
        return cachedRAProgression[raGameId] ?: emptyList()
    }

    /**
     * Every per-game progression row the last refresh returned, award dates and counts included.
     * Empty until a refresh has succeeded this session.
     */
    fun getProgression(): List<RomMRAGameProgression> = cachedProgression

    private fun updateCache(progression: List<RomMRAGameProgression>) {
        cachedProgression = progression
        cachedRAProgression = progression
            .filter { it.romRaId != null }
            .associate { it.romRaId!! to it.earnedAchievements }
    }

    suspend fun refreshRAProgressionOnStartup() {
        refreshRAProgressionIfNeeded()
    }

    /**
     * Asks the server to refresh RetroAchievements progression and caches the result. Runs once
     * after each app resume, or again on [force]; never twice within a minute.
     */
    suspend fun refreshRAProgressionIfNeeded(force: Boolean = false): RomMResult<Unit> = refreshMutex.withLock {
        val sinceLast = SystemClock.elapsedRealtime() - lastRefreshAttemptAt
        if (lastRefreshAttemptAt > 0L && sinceLast < REFRESH_FLOOR_MS) return@withLock RomMResult.Success(Unit)
        if (refreshAttemptedThisSession && !force) return@withLock RomMResult.Success(Unit)

        val currentApi = api ?: return@withLock RomMResult.Error("Not connected")
        refreshAttemptedThisSession = true
        lastRefreshAttemptAt = SystemClock.elapsedRealtime()
        try {
            val user = connectionManager.currentUser ?: run {
                val userResponse = currentApi.getCurrentUser()
                if (!userResponse.isSuccessful) {
                    return@withLock RomMResult.Error("Failed to get user", userResponse.code())
                }
                userResponse.body() ?: return@withLock RomMResult.Error("No user data")
            }
            if (user.raUsername.isNullOrBlank()) {
                return@withLock RomMResult.Error("No RetroAchievements username configured")
            }
            if (cachedProgression.isEmpty()) user.raProgression?.results?.let(::updateCache)

            val response = currentApi.refreshRAProgression(user.id)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "Failed to refresh RA progression: HTTP ${response.code()}; preserving existing cache")
                return@withLock RomMResult.Error("Failed to refresh RA progression: HTTP ${response.code()}")
            }

            val refreshedUserResponse = currentApi.getCurrentUser()
            val progression = if (refreshedUserResponse.isSuccessful) {
                refreshedUserResponse.body()?.raProgression?.results
            } else {
                Logger.warn(TAG, "Post-refresh user fetch failed (${refreshedUserResponse.code()}); preserving existing cache")
                null
            }
            if (progression != null) updateCache(progression)

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.warn(TAG, "RA progression refresh threw; preserving existing cache: ${e.message}")
            RomMResult.Error(e.message ?: "Failed to refresh RA progression")
        }
    }
}
