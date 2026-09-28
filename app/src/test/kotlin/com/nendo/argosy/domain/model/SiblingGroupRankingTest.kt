package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SiblingGroupRankingTest {

    private val usaFirst = listOf("USA", "Europe", "Japan")
    private val japanFirst = listOf("Japan", "USA", "Europe")

    private fun member(
        id: Long,
        hack: Boolean = false,
        main: Boolean = false,
        preRelease: Boolean = false,
        translation: Boolean = false,
        regions: List<String> = listOf("USA"),
        fileName: String = "Game (USA).sfc"
    ) = SiblingMember(id, hack, main, preRelease, translation, regions, fileName)

    private fun visible(
        members: List<SiblingMember>,
        pick: Long? = null,
        priority: List<String> = usaFirst
    ) = SiblingGroupRanking.visibleMembers(members, pick, priority)

    @Test
    fun `the local pick beats the RomM main in either order`() {
        val a = member(1, fileName = "a")
        val b = member(2, fileName = "b")

        assertEquals(setOf(1L), visible(listOf(a, b.copy(isRommMain = true)), pick = 1))
        assertEquals(setOf(2L), visible(listOf(a.copy(isRommMain = true), b), pick = 2))
    }

    @Test
    fun `a pick that is not in the group is ignored`() {
        val a = member(1, fileName = "a")
        val b = member(2, fileName = "b")

        assertEquals(setOf(1L), visible(listOf(a, b), pick = 99))
    }

    @Test
    fun `the RomM main beats a full release in either order`() {
        val beta = member(1, preRelease = true, main = true, fileName = "a")
        val full = member(2, fileName = "b")

        assertEquals(setOf(1L), visible(listOf(beta, full)))
        assertEquals(
            setOf(2L),
            visible(listOf(beta.copy(isRommMain = false), full.copy(isRommMain = true)))
        )
    }

    @Test
    fun `several mains are resolved by region priority`() {
        val usa = member(1, main = true, regions = listOf("USA"), fileName = "z")
        val japan = member(2, main = true, regions = listOf("Japan"), fileName = "a")
        val europeNotMain = member(3, regions = listOf("Europe"), fileName = "a")

        assertEquals(setOf(1L), visible(listOf(usa, japan, europeNotMain), priority = usaFirst))
        assertEquals(setOf(2L), visible(listOf(usa, japan, europeNotMain), priority = japanFirst))
    }

    @Test
    fun `a full release beats a pre-release in either order`() {
        val full = member(1, regions = listOf("Japan"), fileName = "z")
        val beta = member(2, preRelease = true, regions = listOf("USA"), fileName = "a")

        assertEquals(setOf(1L), visible(listOf(full, beta)))
        assertEquals(
            setOf(2L),
            visible(listOf(full.copy(isPreRelease = true), beta.copy(isPreRelease = false)))
        )
    }

    @Test
    fun `two full releases fall through to region priority`() {
        val usa = member(1, regions = listOf("USA"), fileName = "z")
        val japan = member(2, regions = listOf("Japan"), fileName = "a")

        assertEquals(setOf(1L), visible(listOf(usa, japan), priority = usaFirst))
        assertEquals(setOf(2L), visible(listOf(usa, japan), priority = japanFirst))
    }

    @Test
    fun `a translation ranks with pre-releases, below any full release`() {
        val translation = member(1, translation = true, regions = listOf("USA"), fileName = "a")
        val japanese = member(2, regions = listOf("Japan"), fileName = "z")

        assertEquals(setOf(2L), visible(listOf(translation, japanese)))
        assertEquals(
            setOf(1L),
            visible(listOf(translation.copy(isTranslation = false), japanese.copy(isTranslation = true)))
        )
    }

    @Test
    fun `a translation and a pre-release tie on release tier`() {
        val translation = member(1, translation = true, regions = listOf("Japan"), fileName = "a")
        val beta = member(2, preRelease = true, regions = listOf("USA"), fileName = "z")

        assertEquals(setOf(2L), visible(listOf(translation, beta), priority = usaFirst))
        assertEquals(setOf(1L), visible(listOf(translation, beta), priority = japanFirst))
    }

    @Test
    fun `a picked translation is the visible entry`() {
        val translation = member(1, translation = true, fileName = "a")
        val full = member(2, fileName = "b")

        assertEquals(setOf(1L), visible(listOf(translation, full), pick = 1))
    }

    @Test
    fun `the earliest listed region any member carries decides`() {
        val multi = member(1, regions = listOf("Japan", "Europe"), fileName = "z")
        val usa = member(2, regions = listOf("USA"), fileName = "a")

        assertEquals(setOf(2L), visible(listOf(multi, usa), priority = usaFirst))
        assertEquals(setOf(1L), visible(listOf(multi, usa), priority = listOf("Europe", "USA")))
    }

    @Test
    fun `region matching ignores case`() {
        val lower = member(1, regions = listOf("japan"), fileName = "z")
        val usa = member(2, regions = listOf("USA"), fileName = "a")

        assertEquals(setOf(1L), visible(listOf(lower, usa), priority = japanFirst))
    }

    @Test
    fun `an untagged member ranks after a tagged one in either order`() {
        val untagged = member(1, regions = emptyList(), fileName = "a")
        val tagged = member(2, regions = listOf("Japan"), fileName = "z")

        assertEquals(setOf(2L), visible(listOf(untagged, tagged)))
        assertEquals(
            setOf(1L),
            visible(listOf(untagged.copy(regions = listOf("Japan")), tagged.copy(regions = emptyList())))
        )
    }

    @Test
    fun `a region outside the priority list ties with an untagged member`() {
        val unlisted = member(1, regions = listOf("Brazil"), fileName = "b")
        val untagged = member(2, regions = emptyList(), fileName = "a")

        assertEquals(setOf(2L), visible(listOf(unlisted, untagged)))
    }

    @Test
    fun `equal regions fall through to file name in either order`() {
        val a = member(1, fileName = "Game (USA) (Rev 1).sfc")
        val b = member(2, fileName = "Game (USA).sfc")

        assertEquals(setOf(1L), visible(listOf(a, b)))
        assertEquals(setOf(2L), visible(listOf(a.copy(fileName = "b"), b.copy(fileName = "a"))))
    }

    @Test
    fun `file names compare without case`() {
        val upper = member(1, fileName = "B")
        val lower = member(2, fileName = "a")

        assertEquals(setOf(2L), visible(listOf(upper, lower)))
    }

    @Test
    fun `members equal on every tier resolve to the lowest game id in any input order`() {
        val first = member(7)
        val second = member(3)

        assertEquals(setOf(3L), visible(listOf(first, second)))
        assertEquals(setOf(3L), visible(listOf(second, first)))
    }

    @Test
    fun `an unpicked hack keeps its own entry beside the representative`() {
        val base = member(1, fileName = "a")
        val other = member(2, regions = listOf("Japan"), fileName = "b")
        val hack = member(3, hack = true, fileName = "a")

        assertEquals(setOf(1L, 3L), visible(listOf(base, other, hack)))
    }

    @Test
    fun `a hack never wins by region or file name`() {
        val japan = member(1, regions = listOf("Japan"), fileName = "z")
        val hack = member(2, hack = true, regions = listOf("USA"), fileName = "a")

        assertEquals(setOf(1L, 2L), visible(listOf(japan, hack)))
    }

    @Test
    fun `a picked hack is the group's entry and hides the base members`() {
        val base = member(1, fileName = "a")
        val other = member(2, fileName = "b")
        val hack = member(3, hack = true, fileName = "c")

        assertEquals(setOf(3L), visible(listOf(base, other, hack), pick = 3))
    }

    @Test
    fun `a picked hack leaves other hacks as their own entries`() {
        val base = member(1, fileName = "a")
        val picked = member(2, hack = true, fileName = "b")
        val otherHack = member(3, hack = true, fileName = "c")

        assertEquals(setOf(2L, 3L), visible(listOf(base, picked, otherHack), pick = 2))
    }

    @Test
    fun `a hack that is the RomM main hides the base members`() {
        val base = member(1, fileName = "a")
        val hack = member(2, hack = true, main = true, fileName = "b")

        assertEquals(setOf(2L), visible(listOf(base, hack)))
    }

    @Test
    fun `a group of only hacks shows every one`() {
        val one = member(1, hack = true)
        val two = member(2, hack = true)

        assertEquals(setOf(1L, 2L), visible(listOf(one, two)))
    }

    @Test
    fun `a group of one is visible whatever it is`() {
        assertEquals(setOf(5L), visible(listOf(member(5, hack = true, preRelease = true))))
        assertEquals(setOf(6L), visible(listOf(member(6, translation = true))))
    }

    @Test
    fun `an empty group has nothing to show`() {
        assertEquals(emptySet<Long>(), visible(emptyList()))
    }
}
