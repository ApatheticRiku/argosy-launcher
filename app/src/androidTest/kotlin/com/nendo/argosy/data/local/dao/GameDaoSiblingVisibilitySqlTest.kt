package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GameDaoSiblingVisibilitySqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao

    private val platformId = 1L
    private val shown = 1L
    private val hidden = 2L
    private val solo = 3L

    private fun game(
        id: Long,
        title: String,
        group: String?,
        visible: Boolean,
        regions: String? = null,
        genre: String? = null,
        localPath: String? = null,
        coverPath: String? = null,
        favorite: Boolean = false,
        lastPlayed: Instant? = null,
        playCount: Int = 0,
        playTimeMinutes: Int = 0,
        franchises: String? = null
    ) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = "snes",
        title = title,
        sortTitle = title.lowercase(),
        localPath = localPath,
        rommId = 100L + id,
        igdbId = null,
        source = if (localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
        coverPath = coverPath,
        regions = regions,
        genre = genre,
        franchises = franchises,
        isFavorite = favorite,
        lastPlayed = lastPlayed,
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        siblingGroupKey = group,
        isGroupVisible = visible
    )

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameDao = db.gameDao()
        db.platformDao().insert(
            PlatformEntity(id = platformId, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        gameDao.insert(
            game(
                id = shown, title = "Zelda", group = "igdb-1-5", visible = true,
                regions = "USA", genre = "Adventure", localPath = "/roms/1.sfc",
                coverPath = "/covers/1.jpg", playTimeMinutes = 10, franchises = "Zelda"
            )
        )
        gameDao.insert(
            game(
                id = hidden, title = "Zelda", group = "igdb-1-5", visible = false,
                regions = "Japan", genre = "Action RPG", localPath = "/roms/2.sfc",
                coverPath = "/covers/2.jpg", favorite = true, lastPlayed = Instant.now(),
                playCount = 1, playTimeMinutes = 30, franchises = "Zelda"
            )
        )
        gameDao.insert(game(id = solo, title = "Metroid", group = null, visible = true))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ids(items: List<Any>): Set<Long> = items.map {
        when (it) {
            is GameEntity -> it.id
            is GameListItem -> it.id
            is SearchCandidate -> it.id
            is RandomCandidate -> it.id
            else -> error("unexpected row $it")
        }
    }.toSet()

    @Test
    fun library_lists_show_one_row_per_group() = runBlocking {
        assertEquals(setOf(shown, solo), ids(gameDao.observeAllList(null).first()))
        assertEquals(setOf(shown, solo), ids(gameDao.observeByPlatformList(platformId, null).first()))
        assertEquals(setOf(shown), ids(gameDao.observePlayableList(null).first()))
        assertEquals(setOf(shown), ids(gameDao.observePlayableByPlatformList(platformId, null).first()))
    }

    @Test
    fun home_platform_rows_and_detail_navigation_show_one_row_per_group() = runBlocking {
        assertEquals(setOf(shown, solo), ids(gameDao.getByPlatformSorted(platformId, null, 20)))
        assertEquals(setOf(shown, solo), ids(gameDao.getByPlatformTitleOrdered(platformId, null, 20, 0)))
        assertEquals(setOf(shown, solo), ids(gameDao.getByPlatform(platformId, null)))
    }

    @Test
    fun search_matches_only_the_visible_entry() = runBlocking {
        assertEquals(setOf(shown), ids(gameDao.search("zelda", null).first()))
        assertEquals(setOf(shown, solo), ids(gameDao.getSearchCandidates(null)))
    }

    @Test
    fun counts_count_groups() = runBlocking {
        assertEquals(2, gameDao.countByPlatform(platformId, null))
        assertEquals(listOf(PlatformGameCount(platformId, 2)), gameDao.countsByPlatform(null))
        assertEquals(listOf(PlatformGameCount(platformId, 2)), gameDao.observeCountsByPlatform(null).first())
        assertEquals(
            listOf(PlatformGameCount(platformId, 1)),
            gameDao.observeDownloadedCountsByPlatform(null).first()
        )
    }

    @Test
    fun stats_count_groups_and_sum_every_member_play_time() = runBlocking {
        val stats = gameDao.statsByPlatform(null).single()

        assertEquals(2, stats.gameCount)
        assertEquals(1, stats.installedCount)
        assertEquals(40, stats.playTimeMinutes)
    }

    @Test
    fun filter_options_come_from_visible_rows() = runBlocking {
        assertEquals(listOf("USA"), gameDao.getDistinctRegions(null))
        assertEquals(listOf("Adventure"), gameDao.getDistinctGenres(null, oneEntryPerGroup = true))
        assertEquals(
            setOf("Adventure", "Action RPG"),
            gameDao.getDistinctGenres(null, oneEntryPerGroup = false).toSet()
        )
    }

    @Test
    fun showcase_covers_collapse_only_when_asked() = runBlocking {
        assertEquals(listOf("/covers/1.jpg"), gameDao.showcaseCovers(null, null, oneEntryPerGroup = true))
        assertEquals(
            setOf("/covers/1.jpg", "/covers/2.jpg"),
            gameDao.showcaseCovers(null, null, oneEntryPerGroup = false).toSet()
        )
    }

    @Test
    fun favorites_keep_the_hidden_member() = runBlocking {
        assertEquals(setOf(hidden), ids(gameDao.getFavorites(null)))
        assertEquals(setOf(hidden), ids(gameDao.observeFavoritesList(null).first()))
    }

    @Test
    fun recently_played_and_play_history_keep_the_hidden_member() = runBlocking {
        assertEquals(setOf(hidden), ids(gameDao.getRecentlyPlayed(null, 20)))
        assertEquals(setOf(hidden), ids(gameDao.observeRecentlyPlayed(null, 20).first()))
        assertEquals(setOf(hidden), ids(gameDao.getPlayedGames(null)))
    }

    @Test
    fun recommendations_keep_the_hidden_member() = runBlocking {
        gameDao.insert(
            game(id = 4L, title = "Zelda", group = "igdb-1-5", visible = false, localPath = "/roms/4.sfc")
        )

        assertTrue(4L in ids(gameDao.getUnplayedInstalledGames(null)))
    }

    @Test
    fun collections_keep_the_hidden_member() = runBlocking {
        val collectionDao = db.collectionDao()
        val collectionId = collectionDao.insertCollection(CollectionEntity(name = "Favourites"))
        collectionDao.addGameToCollection(CollectionGameEntity(collectionId = collectionId, gameId = hidden))

        assertEquals(setOf(hidden), ids(collectionDao.getGamesInCollection(collectionId)))
    }

    @Test
    fun related_games_random_and_pickers_keep_the_hidden_member() = runBlocking {
        val related = gameDao.getRelatedByFranchise(
            token = "Zelda",
            excludeGameId = solo,
            excludeIgdbId = null,
            excludePlatformId = platformId,
            ownerUserId = null,
            limit = 10
        )
        assertTrue(hidden in ids(related))
        assertTrue(hidden in ids(gameDao.getRandomCandidates(null, false, false, 0, emptyList())))
        assertTrue(hidden in ids(gameDao.searchForQuickMenu("zelda", null, 10).first()))
        assertEquals(setOf(hidden), ids(gameDao.getByIds(listOf(hidden))))
    }
}
