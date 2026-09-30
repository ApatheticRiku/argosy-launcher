package com.nendo.argosy.domain.model

import com.nendo.argosy.data.local.dao.CollectionStats

data class CollectionSummary(
    val gameCount: Int = 0,
    val installedCount: Int = 0,
    val earnedAchievements: Int = 0,
    val totalAchievements: Int = 0,
    val playTimeMinutes: Int = 0
)

fun CollectionStats?.toSummary(): CollectionSummary = this?.let {
    CollectionSummary(
        gameCount = it.gameCount,
        installedCount = it.installedCount,
        earnedAchievements = it.earnedAchievements,
        totalAchievements = it.totalAchievements,
        playTimeMinutes = it.playTimeMinutes
    )
} ?: CollectionSummary()
