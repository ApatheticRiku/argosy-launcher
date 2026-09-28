package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "main-sibling-drop-migration-test.db"

@RunWith(AndroidJUnit4::class)
class Migration199To200DataTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java,
    )

    private val gameColumns = listOf(
        "id", "platformId", "platformSlug", "title", "sortTitle", "searchTitle", "localPath",
        "fileOrigin", "rommId", "rommFileName", "igdbId", "launcherSetManually", "source",
        "coverPath", "coverOverridePath", "regions", "hasManual", "remoteHasSoundtrack",
        "isIdentified", "userRating", "userDifficulty", "completion", "status", "backlogged",
        "nowPlaying", "isFavorite", "playCount", "playTimeMinutes", "lastPlayed", "addedAt",
        "isMultiDisc", "achievementCount", "earnedAchievementCount", "titleIdLocked",
        "hasFileOnDisk", "storeEnrichStatus", "cheatsFetched", "raIdVerified",
        "perGameSettingsEnabled", "perGameControlsEnabled", "syncDirty", "siblingGroupKey",
        "isHackVariant", "isTranslationVariant", "isGroupVisible"
    )

    private val gameValues = listOf<Any?>(
        7L, 1L, "snes", "Chrono Trigger", "chrono trigger", "chrono trigger",
        "/roms/snes/Chrono Trigger (USA).sfc", "ROMM_DOWNLOAD", 42L, "Chrono Trigger (USA).sfc",
        1234L, 0L, "ROMM_REMOTE", "/img/snes/covers/cover_42.jpg",
        "/img/snes/covers/cover_manual_7.jpg", "USA", 1L, 0L, 1L, 4L, 2L, 80L, "playing", 0L,
        1L, 1L, 3L, 95L, 1_700_000_000_000L, 1_600_000_000_000L, 0L, 54L, 12L, 0L, 1L, 1L, 0L,
        0L, 1L, 0L, 1L, "igdb-1-1234", 1L, 0L, 1L
    )

    private val overlayColumns = listOf(
        "id", "ownerUserId", "gameId", "isMember", "serverHidden", "isFavorite", "userRating",
        "userDifficulty", "completion", "status", "backlogged", "nowPlaying", "playCount",
        "playTimeMinutes", "lastPlayed", "earnedAchievementCount", "rommMainSibling"
    )

    private val overlayValues = listOf<Any?>(
        3L, 5L, 7L, 1L, 0L, 1L, 4L, 2L, 80L, "playing", 0L, 1L, 3L, 95L, 1_700_000_000_000L, 12L, 1L
    )

    private fun SupportSQLiteDatabase.insert(table: String, columns: List<String>, values: List<Any?>) {
        execSQL(
            "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) " +
                "VALUES (${columns.joinToString { "?" }})",
            values.toTypedArray()
        )
    }

    private fun SupportSQLiteDatabase.row(table: String, columns: List<String>): List<Any?> =
        query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table`").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            columns.indices.map { index ->
                when {
                    cursor.isNull(index) -> null
                    cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    else -> cursor.getString(index)
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

    @Test
    fun the_main_sibling_column_goes_and_every_other_value_survives() {
        helper.createDatabase(TEST_DB, 199).use { db ->
            db.execSQL(
                "INSERT INTO `platforms` (`id`, `slug`, `name`, `shortName`, `sortOrder`, " +
                    "`isVisible`, `romExtensions`, `gameCount`, `syncEnabled`, `combineContent`) " +
                    "VALUES (1, 'snes', 'Super Nintendo', 'SNES', 0, 1, 'sfc', 1, 1, 0)"
            )
            db.insert("games", gameColumns + "rommMainSibling", gameValues + 1L)
            db.insert("game_user_overlay", overlayColumns, overlayValues)
        }

        helper.runMigrationsAndValidate(TEST_DB, 200, true, Migration_199_200).use { db ->
            assertFalse("rommMainSibling" in db.columnsOf("games"))
            assertEquals(gameValues, db.row("games", gameColumns))
            assertEquals(overlayValues, db.row("game_user_overlay", overlayColumns))
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
}
