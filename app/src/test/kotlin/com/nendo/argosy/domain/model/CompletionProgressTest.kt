package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TEN_HOURS_SEC = 36_000

class CompletionProgressTest {

    @Test
    fun `a recorded completion wins over the play time estimate`() {
        assertEquals(
            CompletionProgress.UserSet(40),
            CompletionProgress.resolve(userCompletion = 40, playTimeMinutes = 540, timeToBeatMainSec = TEN_HOURS_SEC)
        )
    }

    @Test
    fun `a recorded completion wins even when the estimate is lower`() {
        assertEquals(
            CompletionProgress.UserSet(90),
            CompletionProgress.resolve(userCompletion = 90, playTimeMinutes = 60, timeToBeatMainSec = TEN_HOURS_SEC)
        )
    }

    @Test
    fun `a recorded completion stands without any time data`() {
        assertEquals(
            CompletionProgress.UserSet(25),
            CompletionProgress.resolve(userCompletion = 25, playTimeMinutes = 0, timeToBeatMainSec = null)
        )
    }

    @Test
    fun `zero completion means unset and falls through to the estimate`() {
        assertEquals(
            CompletionProgress.Estimate(50),
            CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 300, timeToBeatMainSec = TEN_HOURS_SEC)
        )
    }

    @Test
    fun `the estimate is capped at one hundred`() {
        assertEquals(
            CompletionProgress.Estimate(100),
            CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 1_200, timeToBeatMainSec = TEN_HOURS_SEC)
        )
    }

    @Test
    fun `a recorded completion above one hundred is capped`() {
        assertEquals(
            CompletionProgress.UserSet(100),
            CompletionProgress.resolve(userCompletion = 130, playTimeMinutes = 0, timeToBeatMainSec = null)
        )
    }

    @Test
    fun `no main story time gives no estimate`() {
        assertNull(CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 300, timeToBeatMainSec = null))
        assertNull(CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 300, timeToBeatMainSec = 0))
    }

    @Test
    fun `no play time gives no estimate`() {
        assertNull(CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 0, timeToBeatMainSec = TEN_HOURS_SEC))
    }

    @Test
    fun `play time too small to register a percent gives no bar`() {
        assertNull(CompletionProgress.resolve(userCompletion = 0, playTimeMinutes = 1, timeToBeatMainSec = TEN_HOURS_SEC))
    }
}
