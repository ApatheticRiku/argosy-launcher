package com.nendo.argosy.domain.model

private const val FULL_PERCENT = 100
private const val SECONDS_PER_MINUTE = 60L

/**
 * How far through a game the player is. [UserSet] is the RomM completion percentage, where zero
 * means unset; [Estimate] is play time over the HowLongToBeat main-story time, capped at 100.
 * RetroAchievements progress feeds neither.
 */
sealed interface CompletionProgress {
    val percent: Int

    data class UserSet(override val percent: Int) : CompletionProgress

    data class Estimate(override val percent: Int) : CompletionProgress

    companion object {
        fun resolve(
            userCompletion: Int,
            playTimeMinutes: Int,
            timeToBeatMainSec: Int?
        ): CompletionProgress? {
            if (userCompletion > 0) return UserSet(userCompletion.coerceAtMost(FULL_PERCENT))
            val mainSec = timeToBeatMainSec?.takeIf { it > 0 } ?: return null
            if (playTimeMinutes <= 0) return null
            val percent = (playTimeMinutes * SECONDS_PER_MINUTE * FULL_PERCENT / mainSec)
                .coerceAtMost(FULL_PERCENT.toLong())
                .toInt()
            return if (percent > 0) Estimate(percent) else null
        }
    }
}
