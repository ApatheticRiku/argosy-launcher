package com.nendo.argosy.data.model

import java.time.Instant
import java.time.LocalDate

data class GameDevicePlay(
    val deviceId: String,
    val deviceName: String,
    val activeMs: Long,
    val sessionCount: Int,
    val lastPlayed: Instant,
    val isThisDevice: Boolean
)

/**
 * One game's play and save history for the current account. [days] is the calendar window,
 * oldest first; [saveDates] are the days in that window on which a save version was cached.
 * [weekHourMs] runs Monday to Sunday, each row 0 to 23, over every session of the game.
 */
data class GameActivitySnapshot(
    val gameId: Long,
    val title: String,
    val platformName: String,
    val coverPath: String?,
    val backgroundPath: String?,
    val days: List<PlayDay>,
    val saveDates: Set<LocalDate>,
    val weekHourMs: List<List<Long>>,
    val totalActiveMs: Long,
    val sessionCount: Int,
    val longestSessionMs: Long,
    val lastPlayed: Instant?,
    val versionCount: Int,
    val devices: List<GameDevicePlay>
)
