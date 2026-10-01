package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GameDaoEnabledPlatformSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao

    private val enabledPlatform = 1L
    private val hiddenPlatform = 2L
    private val syncDisabledPlatform = 3L

    private val playedEnabled = 10L
    private val playedHidden = 11L
    private val playedSyncDisabled = 12L
    private val playedAndroid = 13L
    private val newEnabled = 20L
    private val newHidden = 21L
    private val newSyncDisabled = 22L
    private val newAndroid = 23L

    private fun platform(id: Long, slug: String, visible: Boolean, syncEnabled: Boolean) = PlatformEntity(
        id = id,
        slug = slug,
        name = slug,
        shortName = slug,
        romExtensions = "",
        isVisible = visible,
        syncEnabled = syncEnabled
    )

    private fun game(id: Long, platformId: Long, slug: String, lastPlayed: Instant?) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = slug,
        title = "Game $id",
        sortTitle = "game $id",
        localPath = "/roms/$id",
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY,
        lastPlayed = lastPlayed,
        addedAt = Instant.now()
    )

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameDao = db.gameDao()
        val platformDao = db.platformDao()
        platformDao.insert(platform(enabledPlatform, "snes", visible = true, syncEnabled = true))
        platformDao.insert(platform(hiddenPlatform, "nes", visible = false, syncEnabled = true))
        platformDao.insert(platform(syncDisabledPlatform, "gba", visible = true, syncEnabled = false))
        platformDao.insert(
            PlatformDefinitions.toLocalPlatformEntity(PlatformDefinitions.getBySlug("android")!!)!!
        )
        val played = Instant.now()
        gameDao.insert(game(playedEnabled, enabledPlatform, "snes", played))
        gameDao.insert(game(playedHidden, hiddenPlatform, "nes", played))
        gameDao.insert(game(playedSyncDisabled, syncDisabledPlatform, "gba", played))
        gameDao.insert(game(playedAndroid, LocalPlatformIds.ANDROID, "android", played))
        gameDao.insert(game(newEnabled, enabledPlatform, "snes", null))
        gameDao.insert(game(newHidden, hiddenPlatform, "nes", null))
        gameDao.insert(game(newSyncDisabled, syncDisabledPlatform, "gba", null))
        gameDao.insert(game(newAndroid, LocalPlatformIds.ANDROID, "android", null))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recently_played_skips_hidden_and_sync_disabled_platforms() = runBlocking {
        val expected = setOf(playedEnabled, playedAndroid)
        assertEquals(expected, gameDao.getRecentlyPlayed(null, 20).map { it.id }.toSet())
        assertEquals(expected, gameDao.observeRecentlyPlayed(null, 20).first().map { it.id }.toSet())
    }

    @Test
    fun newly_added_skips_hidden_and_sync_disabled_platforms() = runBlocking {
        val threshold = Instant.EPOCH
        val expected = setOf(newEnabled, newAndroid)
        assertEquals(
            expected,
            gameDao.getNewlyAdded(threshold, null, installedOnly = false, limit = 20).map { it.id }.toSet()
        )
        assertEquals(
            expected,
            gameDao.getNewlyAdded(threshold, null, installedOnly = true, limit = 20).map { it.id }.toSet()
        )
    }
}
