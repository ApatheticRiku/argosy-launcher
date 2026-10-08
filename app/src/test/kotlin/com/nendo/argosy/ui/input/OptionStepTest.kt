package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionStepTest {

    private val options = listOf("a", "b", "c")

    private fun step(current: String, delta: Int): Pair<InputResult, List<String>> {
        val applied = mutableListOf<String>()
        return stepOption(options, current, delta) { applied += it } to applied
    }

    @Test
    fun `a step inside the list applies the neighbour`() {
        val (right, appliedRight) = step("b", 1)
        val (left, appliedLeft) = step("b", -1)

        assertEquals(InputResult.HANDLED, right)
        assertEquals(listOf("c"), appliedRight)
        assertEquals(InputResult.HANDLED, left)
        assertEquals(listOf("a"), appliedLeft)
    }

    @Test
    fun `both ends stop with the boundary sound and apply nothing`() {
        val (pastEnd, appliedEnd) = step("c", 1)
        val (pastStart, appliedStart) = step("a", -1)

        assertEquals(InputResult.handled(SoundType.BOUNDARY), pastEnd)
        assertTrue(appliedEnd.isEmpty())
        assertEquals(InputResult.handled(SoundType.BOUNDARY), pastStart)
        assertTrue(appliedStart.isEmpty())
    }

    @Test
    fun `an unlisted current value steps from the first option`() {
        val (forward, appliedForward) = step("z", 1)
        val (back, appliedBack) = step("z", -1)

        assertEquals(InputResult.HANDLED, forward)
        assertEquals(listOf("b"), appliedForward)
        assertEquals(InputResult.handled(SoundType.BOUNDARY), back)
        assertTrue(appliedBack.isEmpty())
    }
}
