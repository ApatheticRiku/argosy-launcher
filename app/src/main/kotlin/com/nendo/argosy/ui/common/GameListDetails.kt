package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.domain.model.CompletionProgress

data class GameListDetails(
    val releaseYear: Int? = null,
    val genre: String? = null,
    val rating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val completion: CompletionProgress? = null,
    val playTimeMinutes: Int = 0,
    val timeToBeatMainSec: Int? = null,
    val igdbId: Long? = null
)

private fun firstGenre(genre: String?): String? =
    genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

val GameListItem.listDetails: GameListDetails
    get() = GameListDetails(
        releaseYear = releaseYear,
        genre = firstGenre(genre),
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        achievementCount = achievementCount,
        earnedAchievementCount = earnedAchievementCount,
        completion = CompletionProgress.resolve(completion, playTimeMinutes, timeToBeatMainSec),
        playTimeMinutes = playTimeMinutes,
        timeToBeatMainSec = timeToBeatMainSec,
        igdbId = igdbId
    )

val GameEntity.listDetails: GameListDetails
    get() = GameListDetails(
        releaseYear = releaseYear,
        genre = firstGenre(genre),
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        achievementCount = achievementCount,
        earnedAchievementCount = earnedAchievementCount,
        completion = CompletionProgress.resolve(completion, playTimeMinutes, timeToBeatMainSec),
        playTimeMinutes = playTimeMinutes,
        timeToBeatMainSec = timeToBeatMainSec,
        igdbId = igdbId
    )
