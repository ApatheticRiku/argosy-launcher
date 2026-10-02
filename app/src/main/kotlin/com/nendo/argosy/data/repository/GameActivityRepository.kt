package com.nendo.argosy.data.repository

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.model.GameActivitySnapshot
import com.nendo.argosy.data.model.GameDevicePlay
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameActivityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: com.nendo.argosy.data.local.dao.PlatformDao,
    private val playSessionDao: PlaySessionDao,
    private val saveCacheDao: SaveCacheDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend fun load(gameId: Long, calendarDays: Int): GameActivitySnapshot? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays((calendarDays - 1).toLong())

        val sessions = playSessionDao.getSessionsForGameAndOwner(gameId, ownerUserId)
            .filter { it.activePlayMs > 0L }
        val activeByDate = sessions
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { (_, daySessions) -> daySessions.sumOf { it.activePlayMs } }
        val days = (0 until calendarDays).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            PlayDay(date = date, activeMs = activeByDate[date]?.takeIf { it >= MIN_DISPLAY_MS } ?: 0L)
        }

        val versions = saveCacheDao.getByGameAndOwner(gameId, ownerUserId)
        val saveDates = versions
            .map { it.cachedAt.atZone(zone).toLocalDate() }
            .filter { !it.isBefore(firstDay) }
            .toSet()

        val localDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val devices = sessions
            .groupBy { it.deviceId }
            .map { (deviceId, deviceSessions) ->
                val latest = deviceSessions.maxBy { it.startTime }
                GameDevicePlay(
                    deviceId = deviceId,
                    deviceName = "${latest.deviceManufacturer} ${latest.deviceModel}".trim(),
                    activeMs = deviceSessions.sumOf { it.activePlayMs },
                    sessionCount = deviceSessions.size,
                    lastPlayed = latest.startTime,
                    isThisDevice = deviceId == localDeviceId
                )
            }
            .sortedByDescending { it.activeMs }

        GameActivitySnapshot(
            gameId = gameId,
            title = game.title,
            platformName = platformDao.getById(game.platformId)?.name ?: game.platformSlug,
            coverPath = game.displayCoverPath,
            backgroundPath = game.displayBackgroundPath,
            days = days,
            saveDates = saveDates,
            weekHourMs = PlayWeekHourMatrix.build(sessions, zone),
            totalActiveMs = sessions.sumOf { it.activePlayMs },
            sessionCount = sessions.size,
            longestSessionMs = sessions.maxOfOrNull { it.activePlayMs } ?: 0L,
            lastPlayed = sessions.maxOfOrNull { it.startTime },
            versionCount = versions.size,
            devices = devices
        )
    }
}
