package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoArtOverrideSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao
    private lateinit var artDao: GameArtDao

    private val serverCoverUrl = "https://romm/covers/42.png"
    private val cachedCover = "/img/snes/covers/cover_42_server.jpg"
    private val coverOverride = "/img/snes/covers/cover_override_7_a.jpg"

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameDao = db.gameDao()
        artDao = db.gameArtDao()
        db.platformDao().insert(
            PlatformEntity(id = 1L, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        gameDao.insert(
            GameEntity(
                id = 7L,
                platformId = 1L,
                platformSlug = "snes",
                title = "Game",
                sortTitle = "game",
                localPath = null,
                rommId = 42L,
                igdbId = null,
                source = GameSource.ROMM_REMOTE
            )
        )
        artDao.setSourceUrl(7L, ArtSlot.COVER, serverCoverUrl)
        artDao.setCached(7L, ArtSlot.COVER, cachedCover, serverCoverUrl)
        artDao.setGradientColors(7L, "{}")
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun set_and_clear_touch_only_the_override_column_of_the_named_slot() = runBlocking {
        artDao.setOverride(7L, ArtSlot.COVER, coverOverride)
        artDao.setOverride(7L, ArtSlot.LOGO, "/img/snes/logos/logo_override_7_c.png")

        val cover = artDao.get(7L, ArtSlot.COVER.name)!!
        assertEquals(coverOverride, cover.overridePath)
        assertEquals(cachedCover, cover.cachedPath)
        assertEquals(serverCoverUrl, cover.sourceUrl)
        assertNull(cover.gradientColors)
        assertNull(artDao.get(7L, ArtSlot.BACKGROUND.name))

        artDao.clearOverride(7L, ArtSlot.COVER)

        val cleared = artDao.get(7L, ArtSlot.COVER.name)!!
        assertNull(cleared.overridePath)
        assertEquals(cachedCover, cleared.cachedPath)
        assertEquals("/img/snes/logos/logo_override_7_c.png", artDao.get(7L, ArtSlot.LOGO.name)?.overridePath)
    }

    @Test
    fun a_sync_source_write_leaves_cache_and_override_alone() = runBlocking {
        artDao.setOverride(7L, ArtSlot.COVER, coverOverride)

        artDao.setSourceUrl(7L, ArtSlot.COVER, "https://romm/covers/42-v2.png")

        val cover = artDao.get(7L, ArtSlot.COVER.name)!!
        assertEquals("https://romm/covers/42-v2.png", cover.sourceUrl)
        assertEquals(cachedCover, cover.cachedPath)
        assertEquals(serverCoverUrl, cover.cachedFromUrl)
        assertEquals(coverOverride, cover.overridePath)
    }

    @Test
    fun a_changed_source_makes_the_row_pending_until_cached_again() = runBlocking {
        assertTrue(artDao.getPending().none { it.gameId == 7L && it.slot == ArtSlot.COVER.name })

        artDao.setSourceUrl(7L, ArtSlot.COVER, "https://romm/covers/42-v2.png")

        val pending = artDao.getPending().single { it.slot == ArtSlot.COVER.name }
        assertEquals(42L, pending.rommId)
        assertEquals("https://romm/covers/42-v2.png", pending.sourceUrl)

        artDao.setCached(7L, ArtSlot.COVER, "/img/snes/covers/cover_42_v2.jpg", "https://romm/covers/42-v2.png")

        assertTrue(artDao.getPending().isEmpty())
    }

    @Test
    fun platform_cache_clear_keeps_override_and_source_columns() = runBlocking {
        artDao.setOverride(7L, ArtSlot.COVER, coverOverride)
        artDao.setOverride(7L, ArtSlot.BACKGROUND, "/img/snes/backgrounds/bg_override_7_b.jpg")

        assertEquals(
            setOf(coverOverride, "/img/snes/backgrounds/bg_override_7_b.jpg"),
            artDao.getOverridePathsForPlatform("snes").toSet()
        )

        artDao.clearCachedForPlatform("snes")

        val cover = artDao.get(7L, ArtSlot.COVER.name)!!
        assertNull(cover.cachedPath)
        assertNull(cover.cachedFromUrl)
        assertEquals(serverCoverUrl, cover.sourceUrl)
        assertEquals(coverOverride, cover.overridePath)
    }

    @Test
    fun a_new_cached_cover_keeps_override_derived_colours() = runBlocking {
        artDao.setOverride(7L, ArtSlot.COVER, coverOverride)
        artDao.setGradientColors(7L, "{\"override\":true}")

        artDao.setCached(7L, ArtSlot.COVER, "/img/snes/covers/cover_42_new.jpg", serverCoverUrl)

        val cover = artDao.get(7L, ArtSlot.COVER.name)!!
        assertEquals("/img/snes/covers/cover_42_new.jpg", cover.cachedPath)
        assertEquals("{\"override\":true}", cover.gradientColors)
    }

    @Test
    fun a_new_cached_cover_without_an_override_drops_stale_colours() = runBlocking {
        artDao.setCached(7L, ArtSlot.COVER, "/img/snes/covers/cover_42_new.jpg", serverCoverUrl)

        assertNull(artDao.get(7L, ArtSlot.COVER.name)!!.gradientColors)
    }

    @Test
    fun deleting_the_game_deletes_its_art() = runBlocking {
        gameDao.delete(7L)

        assertTrue(artDao.getForGame(7L).isEmpty())
    }
}
