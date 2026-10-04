package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.SaveListState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val OWNER = 42L
private const val OTHER_OWNER = 7L

@RunWith(AndroidJUnit4::class)
class GameListRowSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao
    private lateinit var saveSyncDao: SaveSyncDao

    private val platformId = 1L
    private val gameId = 10L

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameDao = db.gameDao()
        saveSyncDao = db.saveSyncDao()
        db.platformDao().insert(
            PlatformEntity(id = platformId, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        gameDao.insert(
            GameEntity(
                id = gameId,
                platformId = platformId,
                platformSlug = "snes",
                title = "Chrono Trigger",
                sortTitle = "chrono trigger",
                localPath = "/roms/ct.sfc",
                rommId = 99L,
                igdbId = 1234L,
                source = GameSource.ROMM_SYNCED,
                developer = "Square",
                status = "finished",
                completion = 65,
                achievementCount = 40,
                earnedAchievementCount = 12,
                timeToBeatMainSec = 90_000,
                lastPlayed = Instant.now()
            )
        )
        db.gameArtDao().setCached(gameId, ArtSlot.COVER, "/covers/ct.jpg", null)
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun assertCarriesRowFields(item: GameListItem) {
        assertEquals(40, item.achievementCount)
        assertEquals(12, item.earnedAchievementCount)
        assertEquals(65, item.completion)
        assertEquals("finished", item.status)
        assertEquals("Square", item.developer)
        assertEquals(1234L, item.igdbId)
        assertEquals(90_000, item.timeToBeatMainSec)
    }

    @Test
    fun plain_projection_carries_the_row_fields() = runBlocking {
        assertCarriesRowFields(gameDao.observeAllList(null).first().single())
    }

    @Test
    fun aliased_projection_carries_the_row_fields() = runBlocking {
        assertCarriesRowFields(gameDao.observeSyncEnabledGames(null).first().single())
    }

    @Test
    fun cover_projection_carries_the_row_fields() = runBlocking {
        assertCarriesRowFields(gameDao.getRecentlyPlayedWithCovers(null, 10).single())
    }

    private suspend fun sync(gameId: Long, emulator: String, status: String, owner: Long?) {
        saveSyncDao.upsert(
            SaveSyncEntity(
                gameId = gameId,
                rommId = 99L,
                emulatorId = emulator,
                syncStatus = status,
                ownerUserId = owner
            )
        )
    }

    private suspend fun worst(): Map<Long, SaveListState> =
        SaveListState.worstByGame(
            saveSyncDao.observeGameSyncStatuses(OWNER, PendingConflictEntity.ownerScope(OWNER))
                .first()
                .map { it.gameId to it.syncStatus }
        )

    @Test
    fun a_game_reports_its_most_urgent_save_row() = runBlocking {
        sync(gameId, "snes9x", SaveSyncEntity.STATUS_SYNCED, OWNER)
        sync(gameId, "bsnes", SaveSyncEntity.STATUS_SERVER_NEWER, OWNER)
        sync(gameId, "mesen", SaveSyncEntity.STATUS_LOCAL_NEWER, null)

        assertEquals(mapOf(gameId to SaveListState.SERVER_AHEAD), worst())
    }

    @Test
    fun another_owners_rows_are_left_out() = runBlocking {
        sync(gameId, "snes9x", SaveSyncEntity.STATUS_SYNCED, OWNER)
        sync(gameId, "bsnes", SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION, OTHER_OWNER)

        assertEquals(mapOf(gameId to SaveListState.SYNCED), worst())
    }

    @Test
    fun an_open_parked_conflict_outranks_every_sync_row() = runBlocking {
        sync(gameId, "snes9x", SaveSyncEntity.STATUS_SERVER_NEWER, OWNER)
        db.pendingConflictDao().upsert(
            PendingConflictEntity(
                gameId = gameId,
                rommSaveId = 5L,
                fileName = "ct.srm",
                localUpdatedAt = null,
                serverUpdatedAt = null,
                ownerUserId = OWNER
            )
        )

        assertEquals(mapOf(gameId to SaveListState.NEEDS_ATTENTION), worst())
    }

    @Test
    fun a_dismissed_conflict_is_ignored() = runBlocking {
        sync(gameId, "snes9x", SaveSyncEntity.STATUS_SYNCED, OWNER)
        db.pendingConflictDao().upsert(
            PendingConflictEntity(
                gameId = gameId,
                rommSaveId = 5L,
                fileName = "ct.srm",
                localUpdatedAt = null,
                serverUpdatedAt = null,
                dismissed = true,
                ownerUserId = OWNER
            )
        )

        assertEquals(mapOf(gameId to SaveListState.SYNCED), worst())
    }
}
