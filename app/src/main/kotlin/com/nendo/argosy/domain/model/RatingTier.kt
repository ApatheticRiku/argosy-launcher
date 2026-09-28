package com.nendo.argosy.domain.model

private const val MID_FLOOR = 60
private const val HIGH_FLOOR = 80

enum class RatingTier {
    LOW, MID, HIGH;

    companion object {
        fun of(rating: Float): RatingTier {
            val shown = rating.toInt()
            return when {
                shown >= HIGH_FLOOR -> HIGH
                shown >= MID_FLOOR -> MID
                else -> LOW
            }
        }
    }
}
