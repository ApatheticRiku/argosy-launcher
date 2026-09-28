package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncPreferencesRegionPriorityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val known = SyncFilterPreferences.ALL_KNOWN_REGIONS

    private fun TestScope.store(): DataStore<Preferences> {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private suspend fun DataStore<Preferences>.withStoredFilter(
        regions: List<String>,
        mode: RegionFilterMode
    ): DataStore<Preferences> = apply {
        edit {
            it[stringPreferencesKey("sync_filter_regions")] = regions.joinToString(",")
            it[stringPreferencesKey("sync_filter_region_mode")] = mode.name
        }
    }

    private fun leading(vararg first: String): List<String> =
        first.toList() + known.filterNot { it in first }

    @Test
    fun `the seed follows the stored include filter order`() = runTest {
        val repo = SyncPreferencesRepository(
            store().withStoredFilter(listOf("Japan", "USA"), RegionFilterMode.INCLUDE)
        )

        assertEquals(leading("Japan", "USA"), repo.getRegionPriority())
    }

    @Test
    fun `a stored exclude filter seeds the known order`() = runTest {
        val repo = SyncPreferencesRepository(
            store().withStoredFilter(listOf("Japan", "USA"), RegionFilterMode.EXCLUDE)
        )

        assertEquals(known, repo.getRegionPriority())
    }

    @Test
    fun `with no filter the seed is the known order`() = runTest {
        val repo = SyncPreferencesRepository(store())

        assertEquals(known, repo.getRegionPriority())
        assertEquals(known, repo.regionPriority.first())
    }

    @Test
    fun `the seed runs once and later filter changes leave it alone`() = runTest {
        val repo = SyncPreferencesRepository(
            store().withStoredFilter(listOf("Europe"), RegionFilterMode.INCLUDE)
        )
        val seeded = repo.getRegionPriority()

        repo.setSyncFilterRegions(listOf("Korea", "China"))
        repo.setSyncFilterRegionMode(RegionFilterMode.EXCLUDE)

        assertEquals(leading("Europe"), seeded)
        assertEquals(seeded, repo.getRegionPriority())
        assertEquals(seeded, repo.regionPriority.first())
        assertEquals(seeded, repo.preferences.first().regionPriority)
    }

    @Test
    fun `collecting the flow stores the seed`() = runTest {
        val backing = store().withStoredFilter(listOf("Brazil"), RegionFilterMode.INCLUDE)
        val repo = SyncPreferencesRepository(backing)

        val seeded = repo.regionPriority.first()
        backing.edit { it[stringPreferencesKey("sync_filter_regions")] = "Spain" }

        assertEquals(leading("Brazil"), seeded)
        assertEquals(seeded, repo.getRegionPriority())
    }

    @Test
    fun `a filter change before any read seeds from the order it replaces`() = runTest {
        val repo = SyncPreferencesRepository(
            store().withStoredFilter(listOf("Brazil", "Spain"), RegionFilterMode.INCLUDE)
        )

        repo.setSyncFilterRegions(listOf("Spain"))

        assertEquals(leading("Brazil", "Spain"), repo.getRegionPriority())
    }

    @Test
    fun `filter toggles do not change the priority`() = runTest {
        val repo = SyncPreferencesRepository(store())
        val before = repo.getRegionPriority()

        repo.setSyncFilterRegionMode(RegionFilterMode.INCLUDE)
        repo.setSyncFilterRegions(listOf("France"))
        repo.setSyncFilterRegions(listOf("France", "Italy"))
        repo.setSyncFilterRegionMode(RegionFilterMode.EXCLUDE)
        repo.setSyncFilterRegions(emptyList())

        assertEquals(known, before)
        assertEquals(before, repo.getRegionPriority())
        assertEquals(before, repo.preferences.first().regionPriority)
    }

    @Test
    fun `a reorder persists`() = runTest {
        val repo = SyncPreferencesRepository(store())
        val reordered = leading("Australia", "Germany")

        repo.setRegionPriority(reordered)

        assertEquals(reordered, repo.getRegionPriority())
        assertEquals(reordered, repo.regionPriority.first())
        assertEquals(reordered, repo.preferences.first().regionPriority)
    }

    @Test
    fun `a reorder survives a later filter change`() = runTest {
        val repo = SyncPreferencesRepository(
            store().withStoredFilter(listOf("USA"), RegionFilterMode.INCLUDE)
        )
        val reordered = leading("Korea", "USA")
        repo.setRegionPriority(reordered)

        repo.setSyncFilterRegions(listOf("Japan"))

        assertEquals(reordered, repo.getRegionPriority())
    }

    @Test
    fun `a partial reorder is completed with the missing regions`() = runTest {
        val repo = SyncPreferencesRepository(store())

        repo.setRegionPriority(listOf("Taiwan"))

        assertEquals(leading("Taiwan"), repo.getRegionPriority())
    }
}
