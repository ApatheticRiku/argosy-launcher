package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SiblingPickSeedTest {

    private fun candidate(id: Long, group: String, downloaded: Boolean) =
        SeedCandidate(id, group, downloaded)

    @Test
    fun `a group with exactly one downloaded member picks it`() {
        val seeds = SiblingPickSeed.picks(
            listOf(candidate(1, "g", true), candidate(2, "g", false), candidate(3, "g", false)),
            pickedGroupKeys = emptySet()
        )

        assertEquals(mapOf("g" to 1L), seeds)
    }

    @Test
    fun `a group with two downloaded members is left alone`() {
        val seeds = SiblingPickSeed.picks(
            listOf(candidate(1, "g", true), candidate(2, "g", true)),
            pickedGroupKeys = emptySet()
        )

        assertEquals(emptyMap<String, Long>(), seeds)
    }

    @Test
    fun `a group with nothing downloaded is left alone`() {
        val seeds = SiblingPickSeed.picks(
            listOf(candidate(1, "g", false), candidate(2, "g", false)),
            pickedGroupKeys = emptySet()
        )

        assertEquals(emptyMap<String, Long>(), seeds)
    }

    @Test
    fun `a group that already has a pick is left alone`() {
        val seeds = SiblingPickSeed.picks(
            listOf(candidate(1, "g", true), candidate(2, "g", false)),
            pickedGroupKeys = setOf("g")
        )

        assertEquals(emptyMap<String, Long>(), seeds)
    }

    @Test
    fun `a group of one gets no pick`() {
        val seeds = SiblingPickSeed.picks(listOf(candidate(1, "solo", true)), emptySet())

        assertEquals(emptyMap<String, Long>(), seeds)
    }

    @Test
    fun `groups are decided independently`() {
        val seeds = SiblingPickSeed.picks(
            listOf(
                candidate(1, "a", true), candidate(2, "a", false),
                candidate(3, "b", true), candidate(4, "b", true),
                candidate(5, "c", false), candidate(6, "c", true)
            ),
            pickedGroupKeys = emptySet()
        )

        assertEquals(mapOf("a" to 1L, "c" to 6L), seeds)
    }
}
