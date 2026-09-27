package com.nendo.argosy.data.local.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "art-override-migration-test.db"

@RunWith(AndroidJUnit4::class)
class Migration195To196DataTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.insertGame(
        id: Long,
        coverPath: String?,
        originalCoverPath: String?,
        coverSetManually: Boolean,
        backgroundPath: String?
    ) {
        execSQL(
            "INSERT INTO `games` (`id`, `platformId`, `platformSlug`, `title`, `sortTitle`, " +
                "`searchTitle`, `launcherSetManually`, `source`, `coverPath`, `originalCoverPath`, " +
                "`coverSetManually`, `backgroundPath`, `hasManual`, `remoteHasSoundtrack`, " +
                "`isIdentified`, `userRating`, `userDifficulty`, `completion`, `backlogged`, " +
                "`nowPlaying`, `isFavorite`, `playCount`, `playTimeMinutes`, `addedAt`, " +
                "`isMultiDisc`, `achievementCount`, `earnedAchievementCount`, `titleIdLocked`, " +
                "`hasFileOnDisk`, `storeEnrichStatus`, `cheatsFetched`, `raIdVerified`, " +
                "`perGameSettingsEnabled`, `perGameControlsEnabled`, `syncDirty`) " +
                "VALUES (?, 1, 'snes', 'Game', 'game', 'game', 0, 'ROMM_REMOTE', ?, ?, ?, ?, " +
                "0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)",
            arrayOf<Any?>(id, coverPath, originalCoverPath, if (coverSetManually) 1 else 0, backgroundPath)
        )
    }

    private fun SupportSQLiteDatabase.artOf(id: Long): Map<String, String?> {
        query(
            "SELECT `coverPath`, `backgroundPath`, `coverOverridePath`, `backgroundOverridePath`, " +
                "`logoOverridePath` FROM `games` WHERE `id` = $id"
        ).use { cursor ->
            cursor.moveToFirst()
            return (0 until cursor.columnCount).associate { index ->
                cursor.getColumnName(index) to cursor.getString(index)
            }
        }
    }

    @Test
    fun manual_cover_and_screenshot_background_move_to_overrides() {
        helper.createDatabase(TEST_DB, 195).use { db ->
            db.insertGame(
                id = 1,
                coverPath = "/img/snes/covers/cover_manual_1_a.jpg",
                originalCoverPath = "/img/snes/covers/cover_42_server.jpg",
                coverSetManually = true,
                backgroundPath = "/img/snes/backgrounds/bg_custom_1_99.jpg"
            )
            db.insertGame(
                id = 2,
                coverPath = "/img/snes/covers/cover_43_server.jpg",
                originalCoverPath = null,
                coverSetManually = false,
                backgroundPath = "/img/snes/backgrounds/bg_43_server.jpg"
            )
            db.execSQL(
                "INSERT INTO `home_grid_pages` (`ownerUserId`, `sortOrder`, `backgroundKind`, " +
                    "`backgroundPath`, `backgroundGameId`, `audioKind`, `createdAt`) " +
                    "VALUES (NULL, 0, 'GAME_ART', '/img/snes/backgrounds/bg_43_server.jpg', 2, 'GLOBAL', 0)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 196, true, Migration_195_196).use { db ->
            val manual = db.artOf(1)
            assertEquals("/img/snes/covers/cover_42_server.jpg", manual["coverPath"])
            assertEquals("/img/snes/covers/cover_manual_1_a.jpg", manual["coverOverridePath"])
            assertNull(manual["backgroundPath"])
            assertEquals("/img/snes/backgrounds/bg_custom_1_99.jpg", manual["backgroundOverridePath"])
            assertNull(manual["logoOverridePath"])

            val plain = db.artOf(2)
            assertEquals("/img/snes/covers/cover_43_server.jpg", plain["coverPath"])
            assertEquals("/img/snes/backgrounds/bg_43_server.jpg", plain["backgroundPath"])
            assertNull(plain["coverOverridePath"])
            assertNull(plain["backgroundOverridePath"])

            db.query("SELECT `backgroundPath` FROM `home_grid_pages`").use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
            }
        }
    }
}
