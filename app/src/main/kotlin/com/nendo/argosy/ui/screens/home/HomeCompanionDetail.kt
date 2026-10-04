package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats

fun HomeGameUi.toCompanionDetail(friends: List<FriendActivity> = emptyList()): CompanionDetail =
    CompanionDetail(
        title = title,
        subtitle = platformDisplayName,
        platformSlug = platformSlug,
        artUrl = coverPath,
        backdropUrl = backgroundPath,
        isGameTitle = true,
        spineUrl = boxSpinePath,
        logoUrl = logoPath,
        gameId = id,
        stats = CompanionGameStats(
            developer = developer,
            releaseYear = releaseYear,
            players = players,
            genre = genre,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            playTimeMinutes = playTimeMinutes,
            timeToBeatMainSec = timeToBeatMainSec,
            achievementCount = achievementCount,
            earnedAchievementCount = earnedAchievementCount,
            friends = friends
        )
    )
