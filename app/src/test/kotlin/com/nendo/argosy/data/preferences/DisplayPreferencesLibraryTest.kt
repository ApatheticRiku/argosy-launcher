package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.domain.model.PlayerCountBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SNES_ID = 9L
private const val SNES_NAME = "Super Nintendo"

class DisplayPreferencesLibraryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val legacyPlatformKey = stringPreferencesKey("library_default_platform")
    private val platformIdKey = longPreferencesKey("library_default_platform_id")

    private suspend fun TestScope.store(): DataStore<Preferences> {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(scope = scope) { file }.apply {
            edit { it[booleanPreferencesKey("dual_screen_enabled")] = false }
        }
    }

    private val resolver: (String) -> Long? = { name -> if (name == SNES_NAME) SNES_ID else null }

    @Test
    fun `the library layout defaults to grid`() = runTest {
        val repo = DisplayPreferencesRepository(store())

        assertEquals(LibraryLayout.GRID, repo.preferences.first().libraryLayout)
    }

    @Test
    fun `a chosen library layout persists`() = runTest {
        val backing = store()
        DisplayPreferencesRepository(backing).setLibraryLayout(LibraryLayout.LIST)

        assertEquals(LibraryLayout.LIST, DisplayPreferencesRepository(backing).preferences.first().libraryLayout)
    }

    @Test
    fun `the library layout key is its own and leaves grid density alone`() = runTest {
        val repo = DisplayPreferencesRepository(store())
        repo.setGridDensity(GridDensity.COMPACT)

        repo.setLibraryLayout(LibraryLayout.LIST)

        val prefs = repo.preferences.first()
        assertEquals(GridDensity.COMPACT, prefs.gridDensity)
        assertEquals(LibraryLayout.LIST, prefs.libraryLayout)
    }

    @Test
    fun `an unknown stored layout reads as grid`() = runTest {
        val backing = store()
        backing.edit { it[stringPreferencesKey("library_layout")] = "CAROUSEL" }

        assertEquals(LibraryLayout.GRID, DisplayPreferencesRepository(backing).preferences.first().libraryLayout)
    }

    @Test
    fun `a legacy platform name becomes the matching platform id`() = runTest {
        val backing = store()
        backing.edit { it[legacyPlatformKey] = SNES_NAME }
        val repo = DisplayPreferencesRepository(backing)

        repo.migrateLegacyDefaultPlatform(resolver)

        assertEquals(SNES_ID, repo.preferences.first().libraryDefaultPlatformId)
        assertNull(backing.data.first()[legacyPlatformKey])
    }

    @Test
    fun `an unmatched legacy platform name becomes all platforms`() = runTest {
        val backing = store()
        backing.edit { it[legacyPlatformKey] = "Virtual Boy" }
        val repo = DisplayPreferencesRepository(backing)

        repo.migrateLegacyDefaultPlatform(resolver)

        assertNull(repo.preferences.first().libraryDefaultPlatformId)
        assertNull(backing.data.first()[legacyPlatformKey])
    }

    @Test
    fun `a blank legacy platform means all platforms`() = runTest {
        val backing = store()
        backing.edit { it[legacyPlatformKey] = "" }
        val repo = DisplayPreferencesRepository(backing)

        repo.migrateLegacyDefaultPlatform { error("a blank name is never resolved") }

        assertNull(repo.preferences.first().libraryDefaultPlatformId)
    }

    @Test
    fun `the migration runs once`() = runTest {
        val backing = store()
        backing.edit { it[legacyPlatformKey] = SNES_NAME }
        val repo = DisplayPreferencesRepository(backing)
        repo.migrateLegacyDefaultPlatform(resolver)
        repo.setLibraryDefaultPlatformId(null)

        repo.migrateLegacyDefaultPlatform(resolver)

        assertNull(repo.preferences.first().libraryDefaultPlatformId)
    }

    @Test
    fun `an id already stored wins over a leftover legacy name`() = runTest {
        val backing = store()
        backing.edit {
            it[legacyPlatformKey] = SNES_NAME
            it[platformIdKey] = 3L
        }
        val repo = DisplayPreferencesRepository(backing)

        repo.migrateLegacyDefaultPlatform(resolver)

        assertEquals(3L, repo.preferences.first().libraryDefaultPlatformId)
        assertNull(backing.data.first()[legacyPlatformKey])
    }

    @Test
    fun `clearing the default platform removes it`() = runTest {
        val repo = DisplayPreferencesRepository(store())
        repo.setLibraryDefaultPlatformId(SNES_ID)

        repo.setLibraryDefaultPlatformId(null)

        assertNull(repo.preferences.first().libraryDefaultPlatformId)
    }

    @Test
    fun `default regions and players persist as tokens`() = runTest {
        val backing = store()
        val repo = DisplayPreferencesRepository(backing)

        repo.setLibraryDefaultRegions(setOf("Japan", "USA"))
        repo.setLibraryDefaultPlayers(PlayerCountBucket.FOUR_PLUS)

        val prefs = repo.preferences.first()
        assertEquals(setOf("Japan", "USA"), prefs.libraryDefaultRegions)
        assertEquals(PlayerCountBucket.FOUR_PLUS, prefs.libraryDefaultPlayers)
        assertEquals("FOUR_PLUS", backing.data.first()[stringPreferencesKey("library_default_players")])
    }

    @Test
    fun `clearing regions and players leaves no filter`() = runTest {
        val repo = DisplayPreferencesRepository(store())
        repo.setLibraryDefaultRegions(setOf("Japan"))
        repo.setLibraryDefaultPlayers(PlayerCountBucket.TWO)

        repo.setLibraryDefaultRegions(emptySet())
        repo.setLibraryDefaultPlayers(null)

        val prefs = repo.preferences.first()
        assertEquals(emptySet<String>(), prefs.libraryDefaultRegions)
        assertNull(prefs.libraryDefaultPlayers)
    }
}
