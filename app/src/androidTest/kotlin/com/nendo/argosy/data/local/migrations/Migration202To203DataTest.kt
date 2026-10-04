package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "game-art-split-migration-test.db"

@RunWith(AndroidJUnit4::class)
class Migration202To203DataTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java,
    )

    private val keptGameColumns = listOf(
        "id", "platformId", "platformSlug", "title", "sortTitle", "searchTitle", "localPath",
        "fileOrigin", "rommId", "rommFileName", "igdbId", "launcherSetManually", "source",
        "cachedScreenshotPaths", "boxSpinePath", "regions", "hasManual", "remoteHasSoundtrack",
        "isIdentified", "userRating", "userDifficulty", "completion", "status", "backlogged",
        "nowPlaying", "isFavorite", "playCount", "playTimeMinutes", "lastPlayed", "addedAt",
        "isMultiDisc", "achievementCount", "earnedAchievementCount", "titleIdLocked",
        "hasFileOnDisk", "storeEnrichStatus", "cheatsFetched", "raIdVerified",
        "perGameSettingsEnabled", "perGameControlsEnabled", "syncDirty", "siblingGroupKey",
        "isHackVariant", "isTranslationVariant", "isGroupVisible"
    )

    private fun keptGameValues(id: Long, rommId: Long) = listOf<Any?>(
        id, 1L, "snes", "Game $id", "game $id", "game $id",
        "/roms/snes/game$id.sfc", "ROMM_DOWNLOAD", rommId, "game$id.sfc",
        1000L + id, 0L, "ROMM_SYNCED", "/img/snes/screenshots/ss_${rommId}_0_a.jpg",
        "/img/snes/covers/box_spine_${rommId}_a.jpg", "USA", 1L, 0L, 1L, 4L, 2L, 80L, "playing",
        0L, 1L, 1L, 3L, 95L, 1_700_000_000_000L, 1_600_000_000_000L, 0L, 54L, 12L, 0L, 1L, 1L, 0L,
        0L, 1L, 0L, 1L, "igdb-1-$id", 0L, 0L, 1L
    )

    private val artColumns = listOf(
        "coverPath", "backgroundPath", "logoPath", "coverOverridePath", "backgroundOverridePath",
        "logoOverridePath", "gradientColors", "coverAspectRatio"
    )

    private val overlayColumns = listOf(
        "id", "ownerUserId", "gameId", "isMember", "serverHidden", "isFavorite", "userRating",
        "userDifficulty", "completion", "status", "backlogged", "nowPlaying", "playCount",
        "playTimeMinutes", "lastPlayed", "earnedAchievementCount", "rommMainSibling"
    )

    private val overlayValues = listOf<Any?>(
        3L, 5L, 7L, 1L, 0L, 1L, 4L, 2L, 80L, "playing", 0L, 1L, 3L, 95L, 1_700_000_000_000L, 12L, 1L
    )

    private val fileColumns = listOf(
        "id", "gameId", "rommFileId", "romId", "fileName", "filePath", "category", "fileSize",
        "localPath", "isLaunchTarget", "isMultiDisc"
    )

    private val fileValues = listOf<Any?>(
        11L, 7L, 500L, 42L, "Game 7.sfc", "roms/snes", "game", 4096L, "/roms/snes/game7.sfc", 1L, 0L
    )

    private val pickColumns = listOf("id", "ownerUserId", "groupKey", "gameId")
    private val pickValues = listOf<Any?>(21L, 5L, "igdb-1-7", 8L)

    private fun SupportSQLiteDatabase.insert(table: String, columns: List<String>, values: List<Any?>) {
        execSQL(
            "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) " +
                "VALUES (${columns.joinToString { "?" }})",
            values.toTypedArray()
        )
    }

    private fun Cursor.valueAt(index: Int): Any? = when {
        isNull(index) -> null
        getType(index) == Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        getType(index) == Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
        else -> getString(index)
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<Any?>> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { cursor.valueAt(it) })
                }
            }
        }

    private fun SupportSQLiteDatabase.columnsOf(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    private fun SupportSQLiteDatabase.indicesOf(table: String): Set<String> =
        query("PRAGMA index_list(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    private fun SupportSQLiteDatabase.art(gameId: Long, slot: String): List<Any?>? =
        rows(
            "SELECT `sourceUrl`, `cachedPath`, `cachedFromUrl`, `overridePath`, `gradientColors`, " +
                "`coverAspectRatio` FROM `game_art` WHERE `gameId` = $gameId AND `slot` = '$slot'"
        ).singleOrNull()

    @Test
    fun art_moves_to_its_own_table_and_every_child_row_survives() {
        helper.createDatabase(TEST_DB, 202).use { db ->
            db.execSQL(
                "INSERT INTO `platforms` (`id`, `slug`, `name`, `shortName`, `sortOrder`, " +
                    "`isVisible`, `romExtensions`, `gameCount`, `syncEnabled`, `combineContent`) " +
                    "VALUES (1, 'snes', 'Super Nintendo', 'SNES', 0, 1, 'sfc', 3, 1, 0)"
            )
            db.insert(
                "games",
                keptGameColumns + artColumns,
                keptGameValues(7L, 42L) + listOf(
                    "/img/snes/covers/cover_42_abc.jpg",
                    "https://romm.example/bg/42.jpg",
                    null,
                    "/img/snes/covers/cover_override_7_a.jpg",
                    null,
                    "/img/snes/logos/logo_override_7_c.png",
                    "{\"balanced\":1}",
                    0.75
                )
            )
            db.insert(
                "games",
                keptGameColumns + artColumns,
                keptGameValues(8L, 43L) + listOf(
                    "https://romm.example/covers/43.png",
                    "",
                    "/img/snes/covers/game_logo_43_def.png",
                    null, null, null, null, null
                )
            )
            db.insert(
                "games",
                keptGameColumns + artColumns,
                keptGameValues(9L, 44L) + listOf(null, null, null, null, null, null, null, null)
            )
            db.insert("game_user_overlay", overlayColumns, overlayValues)
            db.insert("game_files", fileColumns, fileValues)
            db.insert("game_group_picks", pickColumns, pickValues)
        }

        helper.runMigrationsAndValidate(TEST_DB, 203, true, Migration_202_203).use { db ->
            val gameColumns = db.columnsOf("games")
            artColumns.forEach { assertFalse(it, it in gameColumns) }
            assertTrue("cachedScreenshotPaths" in gameColumns)

            assertEquals(
                listOf(keptGameValues(7L, 42L), keptGameValues(8L, 43L), keptGameValues(9L, 44L)),
                db.rows(
                    "SELECT ${keptGameColumns.joinToString { "`$it`" }} FROM `games` ORDER BY `id`"
                )
            )

            assertEquals(
                listOf(overlayValues),
                db.rows("SELECT ${overlayColumns.joinToString { "`$it`" }} FROM `game_user_overlay`")
            )
            assertEquals(
                listOf(fileValues),
                db.rows("SELECT ${fileColumns.joinToString { "`$it`" }} FROM `game_files`")
            )
            assertEquals(
                listOf(pickValues),
                db.rows("SELECT ${pickColumns.joinToString { "`$it`" }} FROM `game_group_picks`")
            )

            assertEquals(
                listOf(
                    null,
                    "/img/snes/covers/cover_42_abc.jpg",
                    null,
                    "/img/snes/covers/cover_override_7_a.jpg",
                    "{\"balanced\":1}",
                    0.75
                ),
                db.art(7L, "COVER")
            )
            assertEquals(
                listOf("https://romm.example/bg/42.jpg", null, null, null, null, null),
                db.art(7L, "BACKGROUND")
            )
            assertEquals(
                listOf(null, null, null, "/img/snes/logos/logo_override_7_c.png", null, null),
                db.art(7L, "LOGO")
            )

            assertEquals(
                listOf("https://romm.example/covers/43.png", null, null, null, null, null),
                db.art(8L, "COVER")
            )
            assertEquals(null, db.art(8L, "BACKGROUND"))
            assertEquals(
                listOf(null, "/img/snes/covers/game_logo_43_def.png", null, null, null, null),
                db.art(8L, "LOGO")
            )

            assertTrue(db.rows("SELECT * FROM `game_art` WHERE `gameId` = 9").isEmpty())

            assertEquals(
                setOf(
                    "index_games_platformId", "index_games_title", "index_games_lastPlayed",
                    "index_games_source", "index_games_rommId", "index_games_steamAppId",
                    "index_games_packageName", "index_games_regions", "index_games_gameModes",
                    "index_games_franchises", "index_games_genres", "index_games_collections",
                    "index_games_siblingGroupKey"
                ),
                db.indicesOf("games").filterNot { it.startsWith("sqlite_autoindex_") }.toSet()
            )
        }
    }

    @Test
    fun deleting_a_game_after_migration_cascades_to_its_art() {
        helper.createDatabase(TEST_DB, 202).use { db ->
            db.execSQL(
                "INSERT INTO `platforms` (`id`, `slug`, `name`, `shortName`, `sortOrder`, " +
                    "`isVisible`, `romExtensions`, `gameCount`, `syncEnabled`, `combineContent`) " +
                    "VALUES (1, 'snes', 'Super Nintendo', 'SNES', 0, 1, 'sfc', 1, 1, 0)"
            )
            db.insert(
                "games",
                keptGameColumns + artColumns,
                keptGameValues(7L, 42L) + listOf(
                    "/img/snes/covers/cover_42_abc.jpg", null, null, null, null, null, null, null
                )
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 203, true, Migration_202_203).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM `games` WHERE `id` = 7")

            assertTrue(db.rows("SELECT * FROM `game_art`").isEmpty())
        }
    }
}
