package com.nendo.argosy.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionPriorityTest {

    private val known = SyncFilterPreferences.ALL_KNOWN_REGIONS

    @Test
    fun `include filter order leads the seed and the rest follow in known order`() {
        val filters = SyncFilterPreferences(
            enabledRegions = listOf("Japan", "Europe"),
            regionMode = RegionFilterMode.INCLUDE
        )

        val seed = RegionPriority.seed(filters)

        assertEquals(listOf("Japan", "Europe") + known.filterNot { it == "Japan" || it == "Europe" }, seed)
    }

    @Test
    fun `an empty filter seeds the known order`() {
        val filters = SyncFilterPreferences(enabledRegions = emptyList(), regionMode = RegionFilterMode.INCLUDE)

        assertEquals(known, RegionPriority.seed(filters))
    }

    @Test
    fun `an exclude filter carries no order and seeds the known order`() {
        val filters = SyncFilterPreferences(
            enabledRegions = listOf("Japan", "Europe"),
            regionMode = RegionFilterMode.EXCLUDE
        )

        assertEquals(known, RegionPriority.seed(filters))
    }

    @Test
    fun `a stored order drops unknown and repeated regions and appends missing ones`() {
        val restored = RegionPriority.deserialize("Europe,Atlantis,Europe,USA")

        assertEquals(listOf("Europe", "USA") + known.filterNot { it == "Europe" || it == "USA" }, restored)
        assertEquals(known.size, restored.toSet().size)
    }

    @Test
    fun `serialize and deserialize round trip an order`() {
        val order = known.reversed()

        assertEquals(order, RegionPriority.deserialize(RegionPriority.serialize(order)))
    }
}
