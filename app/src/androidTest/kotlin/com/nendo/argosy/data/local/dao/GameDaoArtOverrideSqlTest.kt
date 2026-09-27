package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoArtOverrideSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameDao = db.gameDao()
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
                source = GameSource.ROMM_REMOTE,
                coverPath = "/img/snes/covers/cover_42_server.jpg",
                backgroundPath = "/img/snes/backgrounds/bg_42_server.jpg",
                logoPath = "/img/snes/covers/game_logo_42_server.png",
                gradientColors = "{}"
            )
        )
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun set_and_clear_touch_only_the_named_slot() = runBlocking {
        gameDao.setArtOverride(7L, ArtSlot.COVER, "/img/snes/covers/cover_override_7_a.jpg")
        gameDao.setArtOverride(7L, ArtSlot.LOGO, "/img/snes/logos/logo_override_7_c.png")

        val set = gameDao.getById(7L)!!
        assertEquals("/img/snes/covers/cover_override_7_a.jpg", set.coverOverridePath)
        assertNull(set.backgroundOverridePath)
        assertEquals("/img/snes/logos/logo_override_7_c.png", set.logoOverridePath)
        assertEquals("/img/snes/covers/cover_42_server.jpg", set.coverPath)
        assertNull(set.gradientColors)

        gameDao.clearArtOverride(7L, ArtSlot.COVER)

        val cleared = gameDao.getById(7L)!!
        assertNull(cleared.coverOverridePath)
        assertEquals("/img/snes/logos/logo_override_7_c.png", cleared.logoOverridePath)
        assertEquals("/img/snes/covers/cover_42_server.jpg", cleared.coverPath)
    }

    @Test
    fun list_projections_show_the_cover_override() = runBlocking {
        gameDao.setArtOverride(7L, ArtSlot.COVER, "/img/snes/covers/cover_override_7_a.jpg")

        val item = gameDao.observeAllList(null).first().single()
        assertEquals("/img/snes/covers/cover_override_7_a.jpg", item.coverPath)
    }

    @Test
    fun platform_cache_clear_keeps_override_columns() = runBlocking {
        gameDao.setArtOverride(7L, ArtSlot.COVER, "/img/snes/covers/cover_override_7_a.jpg")
        gameDao.setArtOverride(7L, ArtSlot.BACKGROUND, "/img/snes/backgrounds/bg_override_7_b.jpg")

        assertEquals(
            setOf(
                "/img/snes/covers/cover_override_7_a.jpg",
                "/img/snes/backgrounds/bg_override_7_b.jpg"
            ),
            gameDao.getArtOverridePathsForPlatform("snes").toSet()
        )

        gameDao.clearCachedArtForPlatform("snes")

        val game = gameDao.getById(7L)!!
        assertNull(game.coverPath)
        assertNull(game.backgroundPath)
        assertEquals("/img/snes/covers/cover_override_7_a.jpg", game.coverOverridePath)
        assertEquals("/img/snes/backgrounds/bg_override_7_b.jpg", game.backgroundOverridePath)
    }

    @Test
    fun a_server_cover_update_keeps_override_derived_colours() = runBlocking {
        gameDao.setArtOverride(7L, ArtSlot.COVER, "/img/snes/covers/cover_override_7_a.jpg")
        gameDao.updateGradientColors(7L, "{\"override\":true}")

        gameDao.updateCoverPath(7L, "/img/snes/covers/cover_42_new.jpg")

        val game = gameDao.getById(7L)!!
        assertEquals("/img/snes/covers/cover_42_new.jpg", game.coverPath)
        assertEquals("{\"override\":true}", game.gradientColors)
    }
}
