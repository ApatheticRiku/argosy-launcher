package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreOptionsActiveVariantTest {

    @Test
    fun `the active variant row is hidden for a single-member group`() {
        val options = buildMoreOptions(MoreOptionsContext(isRommGame = true, hasSiblingGroup = false))

        assertFalse(options.contains(MoreOptionAction.ActiveVariant))
    }

    @Test
    fun `the active variant row shows for a multi-member group, downloaded or not`() {
        listOf(true, false).forEach { downloaded ->
            val options = buildMoreOptions(
                MoreOptionsContext(isRommGame = true, isDownloaded = downloaded, hasSiblingGroup = true)
            )

            assertTrue("downloaded=$downloaded", options.contains(MoreOptionAction.ActiveVariant))
            assertEquals(1, options.count { it == MoreOptionAction.ActiveVariant })
        }
    }

    @Test
    fun `the active variant row sits above the destructive tail`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isRommGame = true, isDownloaded = true, hasSiblingGroup = true)
        )

        assertTrue(options.indexOf(MoreOptionAction.ActiveVariant) < options.indexOf(MoreOptionAction.Delete))
    }
}
