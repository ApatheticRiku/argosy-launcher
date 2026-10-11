package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AyaneoPerformanceTest {

    private val soc = AyaneoSocs.AR14

    @Test
    fun `every mode reads back as itself`() {
        soc.profiles.forEach { (mode, profile) ->
            assertEquals(mode, soc.modeMatching(profile.governor, profile.cpuMaxKhz, profile.gpuMaxHz))
        }
    }

    @Test
    fun `gaming and max are told apart by the gpu cap alone`() {
        val gaming = soc.profiles.getValue(PerformanceMode.GAMING)
        val max = soc.profiles.getValue(PerformanceMode.MAX)
        assertEquals(gaming.cpuMaxKhz, max.cpuMaxKhz)
        assertEquals(PerformanceMode.GAMING, soc.modeMatching("performance", gaming.cpuMaxKhz, 903_000_000))
        assertEquals(PerformanceMode.MAX, soc.modeMatching("performance", max.cpuMaxKhz, 1_050_000_000))
    }

    @Test
    fun `a hand-tuned state matches no mode`() {
        val balanced = soc.profiles.getValue(PerformanceMode.BALANCED)
        assertNull(soc.modeMatching("performance", balanced.cpuMaxKhz, 680_000_000))
    }

    @Test
    fun `the pocket fit resolves to the measured table and nothing else does`() {
        assertEquals(soc, AyaneoPerformance.socFor("AYANEO_Pocket_FIT"))
        assertNull(AyaneoPerformance.socFor("AYANEO_PocketS2"))
        assertNull(AyaneoPerformance.socFor(null))
    }

    @Test
    fun `a mode write sets the floor before the cap on every policy`() {
        val commands = AyaneoPerformance.writeCommands(soc, soc.profiles.getValue(PerformanceMode.ECO))
        soc.policies.forEach { policy ->
            val min = commands.indexOfFirst { it.endsWith("policy$policy/scaling_min_freq") }
            val max = commands.indexOfFirst { it.endsWith("policy$policy/scaling_max_freq") }
            assertTrue(min in 0 until max)
        }
        assertTrue(commands.contains("echo 310000000 > /sys/class/kgsl/kgsl-3d0/max_gpuclk"))
    }
}
