package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingTierTest {

    @Test
    fun `below sixty is low`() {
        assertEquals(RatingTier.LOW, RatingTier.of(0f))
        assertEquals(RatingTier.LOW, RatingTier.of(59f))
        assertEquals(RatingTier.LOW, RatingTier.of(59.9f))
    }

    @Test
    fun `sixty through seventy nine is mid`() {
        assertEquals(RatingTier.MID, RatingTier.of(60f))
        assertEquals(RatingTier.MID, RatingTier.of(79f))
        assertEquals(RatingTier.MID, RatingTier.of(79.9f))
    }

    @Test
    fun `eighty and above is high`() {
        assertEquals(RatingTier.HIGH, RatingTier.of(80f))
        assertEquals(RatingTier.HIGH, RatingTier.of(100f))
    }
}
