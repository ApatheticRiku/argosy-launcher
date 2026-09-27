package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PizzaBoyProRegistryTest {

    @Test
    fun `both Pro packages are recognised as their own emulators`() {
        assertEquals("pizza_boy_gba_pro", EmulatorRegistry.getByPackage("it.dbtecno.pizzaboygbapro")?.id)
        assertEquals("pizza_boy_gb_pro", EmulatorRegistry.getByPackage("it.dbtecno.pizzaboypro")?.id)
    }

    @Test
    fun `the Pro builds are offered on the platforms their free builds cover`() {
        assertTrue(EmulatorRegistry.getForPlatform("gba").any { it.id == "pizza_boy_gba_pro" })
        assertTrue(EmulatorRegistry.getForPlatform("gb").any { it.id == "pizza_boy_gb_pro" })
        assertTrue(EmulatorRegistry.getForPlatform("gbc").any { it.id == "pizza_boy_gb_pro" })
    }

    @Test
    fun `the Pro packages resolve save and state configs in their own data folders`() {
        val gbaSaves = SavePathRegistry.getConfigByPackage("it.dbtecno.pizzaboygbapro")
        val gbSaves = SavePathRegistry.getConfigByPackage("it.dbtecno.pizzaboypro")

        assertEquals("pizza_boy_gba_pro", gbaSaves?.emulatorId)
        assertEquals("pizza_boy_gb_pro", gbSaves?.emulatorId)
        assertTrue(gbaSaves!!.defaultPaths.any { "it.dbtecno.pizzaboygbapro" in it })
        assertTrue(gbSaves!!.defaultPaths.any { "it.dbtecno.pizzaboypro" in it })
        assertNotNull(StatePathRegistry.getConfig("pizza_boy_gba_pro"))
        assertNotNull(StatePathRegistry.getConfig("pizza_boy_gb_pro"))
    }
}
