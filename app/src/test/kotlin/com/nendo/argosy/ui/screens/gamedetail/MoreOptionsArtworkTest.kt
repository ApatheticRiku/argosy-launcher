package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreOptionsArtworkTest {

    @Test
    fun `every kind of game offers the artwork menu`() {
        val contexts = listOf(
            MoreOptionsContext(),
            MoreOptionsContext(isRommGame = true, isDownloaded = true),
            MoreOptionsContext(isSteamGame = true, platformSlug = "steam"),
            MoreOptionsContext(isAndroidApp = true)
        )

        contexts.forEach { ctx ->
            assertTrue(ctx.toString(), buildMoreOptions(ctx).contains(MoreOptionAction.Artwork))
        }
    }

    @Test
    fun `the artwork row appears once`() {
        val options = buildMoreOptions(MoreOptionsContext(isRommGame = true, isDownloaded = true))

        assertEquals(1, options.count { it == MoreOptionAction.Artwork })
    }
}
