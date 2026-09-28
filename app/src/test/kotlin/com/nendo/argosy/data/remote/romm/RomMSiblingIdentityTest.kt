package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomMSiblingIdentityTest {

    private fun rom(
        id: Long = 1L,
        platformId: Long = 4L,
        fileName: String = "Game (USA).nds",
        igdbId: Long? = null,
        ssId: Long? = null,
        mobyId: Long? = null,
        raId: Long? = null,
        hasheousId: Long? = null,
        launchboxId: Long? = null,
        tgdbId: Long? = null,
        flashpointId: String? = null,
        steamId: Long? = null,
        tags: List<String>? = null
    ) = RomMRom(
        id = id,
        platformId = platformId,
        platformSlug = "nds",
        name = "Game",
        slug = null,
        fileName = fileName,
        filePath = null,
        igdbId = igdbId,
        mobyId = mobyId,
        raId = raId,
        ssId = ssId,
        launchboxId = launchboxId,
        hasheousId = hasheousId,
        tgdbId = tgdbId,
        flashpointId = flashpointId,
        steamId = steamId,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        tags = tags
    )

    @Test
    fun `the key takes the first id in RomM's gallery order`() {
        assertEquals("igdb-4-10", RomMSiblingIdentity.groupKey(rom(igdbId = 10, ssId = 20, raId = 30)))
        assertEquals("ss-4-20", RomMSiblingIdentity.groupKey(rom(ssId = 20, mobyId = 5, raId = 30)))
        assertEquals("moby-4-5", RomMSiblingIdentity.groupKey(rom(mobyId = 5, raId = 30)))
        assertEquals("ra-4-30", RomMSiblingIdentity.groupKey(rom(raId = 30, hasheousId = 40)))
        assertEquals("hasheous-4-40", RomMSiblingIdentity.groupKey(rom(hasheousId = 40, launchboxId = 50)))
        assertEquals("launchbox-4-50", RomMSiblingIdentity.groupKey(rom(launchboxId = 50, tgdbId = 60)))
        assertEquals("tgdb-4-60", RomMSiblingIdentity.groupKey(rom(tgdbId = 60, flashpointId = "fp")))
        assertEquals("flashpoint-4-fp", RomMSiblingIdentity.groupKey(rom(flashpointId = "fp", steamId = 70)))
        assertEquals("steam-4-70", RomMSiblingIdentity.groupKey(rom(steamId = 70)))
    }

    @Test
    fun `a rom with no provider id has no group`() {
        assertNull(RomMSiblingIdentity.groupKey(rom()))
    }

    @Test
    fun `the same id on another platform is another group`() {
        assertNotEquals(
            RomMSiblingIdentity.groupKey(rom(platformId = 1, igdbId = 10)),
            RomMSiblingIdentity.groupKey(rom(platformId = 2, igdbId = 10))
        )
    }

    @Test
    fun `HeartGold and SoulSilver sharing only an ra id stay separate groups`() {
        val heartGold = rom(id = 1, igdbId = 1001, ssId = 2001, raId = 7212)
        val soulSilver = rom(id = 2, igdbId = 1002, ssId = 2002, raId = 7212)
        val soulSilverRaOnly = rom(id = 3, raId = 7212)

        assertEquals("igdb-4-1001", RomMSiblingIdentity.groupKey(heartGold))
        assertEquals("igdb-4-1002", RomMSiblingIdentity.groupKey(soulSilver))
        assertNotEquals(RomMSiblingIdentity.groupKey(heartGold), RomMSiblingIdentity.groupKey(soulSilverRaOnly))
    }

    @Test
    fun `regional copies sharing an igdb id share a group`() {
        val usa = rom(id = 1, igdbId = 1001, raId = 7212, fileName = "Game (USA).nds")
        val europe = rom(id = 2, igdbId = 1001, raId = 7212, fileName = "Game (Europe).nds")

        assertEquals(RomMSiblingIdentity.groupKey(usa), RomMSiblingIdentity.groupKey(europe))
    }

    @Test
    fun `a Hack or patched tag marks a hack`() {
        assertTrue(RomMSiblingIdentity.isHack(rom(igdbId = 1, raId = 2, tags = listOf("Hack"))))
        assertTrue(RomMSiblingIdentity.isHack(rom(igdbId = 1, raId = 2, tags = listOf("hack"))))
        assertTrue(RomMSiblingIdentity.isHack(rom(igdbId = 1, hasheousId = 2, tags = listOf("patched-kaizo"))))
        assertTrue(RomMSiblingIdentity.isHack(rom(tags = listOf("USA", "Patched-Randomizer"))))
    }

    @Test
    fun `a translation is never a hack`() {
        val translation = rom(igdbId = 1, tags = listOf("Translation", "Hack"))

        assertTrue(RomMSiblingIdentity.isTranslation(translation))
        assertFalse(RomMSiblingIdentity.isHack(translation))
    }

    @Test
    fun `an identified rom with igdb and ss but no hasheous or ra id is not a hack`() {
        val fireRed = rom(igdbId = 1, ssId = 13181, fileName = "Pokemon - FireRed Version (USA).gba")

        assertFalse(RomMSiblingIdentity.isHack(fireRed))
    }

    @Test
    fun `untagged roms are never hacks`() {
        assertFalse(RomMSiblingIdentity.isHack(rom()))
        assertFalse(RomMSiblingIdentity.isHack(rom(launchboxId = 1, tags = listOf("USA", "Rev 1"))))
        assertFalse(RomMSiblingIdentity.isHack(rom(igdbId = 1, tags = listOf("Hackers Edition"))))
    }

    @Test
    fun `pre-release follows RomM's filename tags`() {
        listOf(
            "Game (Demo).nds", "Game (Beta 2).nds", "Game (Proto).nds", "Game (Prototype).nds",
            "Game (Sample).nds", "Game (Kiosk).nds", "Game (Preview).nds", "Game (demo kiosk).nds"
        ).forEach { assertTrue(it, RomMSiblingIdentity.isPreRelease(it)) }

        listOf("Game (USA).nds", "Demon Castle (USA).nds", "Betamax.nds", null)
            .forEach { assertFalse("$it", RomMSiblingIdentity.isPreRelease(it)) }
    }
}
