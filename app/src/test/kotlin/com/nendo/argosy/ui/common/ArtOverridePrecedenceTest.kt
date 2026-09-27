package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.ui.screens.collections.toCollectionGameUi
import com.nendo.argosy.ui.screens.gamedetail.toGameDetailUi
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtOverridePrecedenceTest {

    private val serverCover = "/cache/snes/covers/cover_42_server.jpg"
    private val serverBackground = "/cache/snes/backgrounds/bg_42_server.jpg"
    private val serverLogo = "/cache/snes/covers/game_logo_42_server.png"
    private val userCover = "/cache/snes/covers/cover_override_9_a.jpg"
    private val userBackground = "/cache/snes/backgrounds/bg_override_9_b.jpg"
    private val userLogo = "/cache/snes/logos/logo_override_9_c.png"

    private val downloadStatus = mockk<DownloadFileStatusRepository>(relaxed = true)

    private fun game(
        coverOverridePath: String? = null,
        backgroundOverridePath: String? = null,
        logoOverridePath: String? = null,
        coverPath: String? = serverCover,
        backgroundPath: String? = serverBackground,
        logoPath: String? = serverLogo
    ) = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "snes",
        title = "Chrono Trigger",
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = 42L,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        coverPath = coverPath,
        backgroundPath = backgroundPath,
        logoPath = logoPath,
        coverOverridePath = coverOverridePath,
        backgroundOverridePath = backgroundOverridePath,
        logoOverridePath = logoOverridePath
    )

    @Test
    fun `display paths prefer each override over its server column`() {
        val overridden = game(userCover, userBackground, userLogo)

        assertEquals(userCover, overridden.displayCoverPath)
        assertEquals(userBackground, overridden.displayBackgroundPath)
        assertEquals(userLogo, overridden.displayLogoPath)
    }

    @Test
    fun `display paths fall back to the server column without an override`() {
        val plain = game()

        assertEquals(serverCover, plain.displayCoverPath)
        assertEquals(serverBackground, plain.displayBackgroundPath)
        assertEquals(serverLogo, plain.displayLogoPath)
    }

    @Test
    fun `an override shows even when the server has no art for that slot`() {
        val overridden = game(
            coverOverridePath = userCover,
            coverPath = null,
            backgroundPath = null,
            logoPath = null
        )

        assertEquals(userCover, overridden.displayCoverPath)
        assertNull(overridden.displayBackgroundPath)
        assertNull(overridden.displayLogoPath)
    }

    @Test
    fun `overridePath answers per slot`() {
        val overridden = game(userCover, null, userLogo)

        assertEquals(userCover, overridden.overridePath(ArtSlot.COVER))
        assertNull(overridden.overridePath(ArtSlot.BACKGROUND))
        assertEquals(userLogo, overridden.overridePath(ArtSlot.LOGO))
    }

    @Test
    fun `home tiles and the companion read the override`() = runTest {
        val ui = game(userCover, userBackground, userLogo).toHomeGameUi(downloadStatus)

        assertEquals(userCover, ui.coverPath)
        assertEquals(userBackground, ui.backgroundPath)
        assertEquals(userLogo, ui.logoPath)
    }

    @Test
    fun `home tiles read the server art without an override`() = runTest {
        val ui = game().toHomeGameUi(downloadStatus)

        assertEquals(serverCover, ui.coverPath)
        assertEquals(serverBackground, ui.backgroundPath)
        assertEquals(serverLogo, ui.logoPath)
    }

    @Test
    fun `library tiles read the cover override`() = runTest {
        val ui = game(coverOverridePath = userCover).toLibraryGameUi(downloadStatus)

        assertEquals(userCover, ui.coverPath)
    }

    @Test
    fun `game detail reads the overrides and flags each overridden slot`() {
        val detail = game(userCover, userBackground).toGameDetailUi(
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        )

        assertEquals(userCover, detail.coverPath)
        assertEquals(userBackground, detail.backgroundPath)
        assertEquals(setOf(ArtSlot.COVER, ArtSlot.BACKGROUND), detail.overriddenArtSlots)
    }

    @Test
    fun `game detail flags a logo override on its own`() {
        val detail = game(logoOverridePath = userLogo).toGameDetailUi(
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        )

        assertEquals(setOf(ArtSlot.LOGO), detail.overriddenArtSlots)
    }

    @Test
    fun `game detail without an override shows server art`() {
        val detail = game().toGameDetailUi(
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        )

        assertEquals(serverCover, detail.coverPath)
        assertEquals(serverBackground, detail.backgroundPath)
        assertTrue(detail.overriddenArtSlots.isEmpty())
    }

    @Test
    fun `collection rows read the cover override`() {
        val row = game(coverOverridePath = userCover).toCollectionGameUi("SNES")

        assertEquals(userCover, row.coverPath)
    }
}
