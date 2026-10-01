package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameFileDaoMissingFilesSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameFileDao: GameFileDao

    private val platformId = 1L
    private val downloadedGame = 1L
    private val partialGame = 2L
    private val remoteGame = 3L

    private fun game(id: Long, localPath: String?) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = "switch",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = localPath,
        rommId = 100L + id,
        igdbId = null,
        source = if (localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
        rommFileName = "game-$id.nsp"
    )

    private fun file(gameId: Long, name: String, localPath: String?) = GameFileEntity(
        gameId = gameId,
        fileName = name,
        filePath = "/remote/$name",
        category = "update",
        fileSize = 1L,
        localPath = localPath
    )

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        gameFileDao = db.gameFileDao()
        db.platformDao().insert(
            PlatformEntity(id = platformId, slug = "switch", name = "Switch", shortName = "Switch", romExtensions = "nsp")
        )
        db.gameDao().insert(game(downloadedGame, "/roms/1.nsp"))
        db.gameDao().insert(game(partialGame, null))
        db.gameDao().insert(game(remoteGame, null))
        gameFileDao.insertAll(
            listOf(
                file(downloadedGame, "d-missing.nsp", null),
                file(downloadedGame, "d-present.nsp", "/roms/d-present.nsp"),
                file(partialGame, "p-missing-a.nsp", null),
                file(partialGame, "p-missing-b.nsp", null),
                file(partialGame, "p-present.nsp", "/roms/p-present.nsp"),
                file(remoteGame, "r-missing-a.nsp", null),
                file(remoteGame, "r-missing-b.nsp", null),
                file(remoteGame, "r-missing-c.nsp", null)
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lists_missing_files_of_downloaded_games_and_of_games_with_a_downloaded_sibling() = runBlocking {
        val missing = gameFileDao.getMissingFilesWithGameInfo()

        assertEquals(
            setOf("d-missing.nsp", "p-missing-a.nsp", "p-missing-b.nsp"),
            missing.map { it.fileName }.toSet()
        )
        assertEquals(3, missing.size)
    }

    @Test
    fun carries_the_owning_game_and_platform() = runBlocking {
        val row = gameFileDao.getMissingFilesWithGameInfo().single { it.fileName == "p-missing-a.nsp" }

        assertEquals(partialGame, row.gameId)
        assertEquals("Game $partialGame", row.gameTitle)
        assertEquals("game-$partialGame.nsp", row.rommFileName)
        assertEquals("switch", row.platformSlug)
        assertEquals(platformId, row.platformId)
    }

    @Test
    fun a_game_with_no_downloaded_file_contributes_nothing() = runBlocking {
        gameFileDao.clearLocalPath(
            gameFileDao.getFilesForGame(partialGame).single { it.fileName == "p-present.nsp" }.id
        )

        val names = gameFileDao.getMissingFilesWithGameInfo().map { it.fileName }.toSet()

        assertEquals(setOf("d-missing.nsp"), names)
    }
}
