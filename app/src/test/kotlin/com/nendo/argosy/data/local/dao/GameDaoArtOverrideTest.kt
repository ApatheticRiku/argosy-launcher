package com.nendo.argosy.data.local.dao

import com.nendo.argosy.data.model.ArtSlot
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameDaoArtOverrideTest {

    private val gameDao = mockk<GameDao>(relaxed = true)

    @Test
    fun `setting a slot writes only that slot`() = runTest {
        gameDao.setArtOverride(7L, ArtSlot.COVER, "/c.jpg")
        gameDao.setArtOverride(7L, ArtSlot.BACKGROUND, "/b.jpg")
        gameDao.setArtOverride(7L, ArtSlot.LOGO, "/l.png")

        coVerify(exactly = 1) { gameDao.setCoverOverride(7L, "/c.jpg") }
        coVerify(exactly = 1) { gameDao.setBackgroundOverride(7L, "/b.jpg") }
        coVerify(exactly = 1) { gameDao.setLogoOverride(7L, "/l.png") }
        confirmVerified(gameDao)
    }

    @Test
    fun `clearing a slot clears only that slot`() = runTest {
        gameDao.clearArtOverride(7L, ArtSlot.BACKGROUND)

        coVerify(exactly = 1) { gameDao.clearBackgroundOverride(7L) }
        coVerify(exactly = 0) { gameDao.clearCoverOverride(any()) }
        coVerify(exactly = 0) { gameDao.clearLogoOverride(any()) }
    }

    @Test
    fun `overridePath on cache info answers per slot`() {
        val info = GameImageCacheInfo(
            id = 7L,
            coverPath = "/server.jpg",
            backgroundPath = null,
            cachedScreenshotPaths = null,
            logoPath = null,
            coverOverridePath = "/user.jpg",
            backgroundOverridePath = null,
            logoOverridePath = "/logo.png"
        )

        assertEquals("/user.jpg", info.overridePath(ArtSlot.COVER))
        assertNull(info.overridePath(ArtSlot.BACKGROUND))
        assertEquals("/logo.png", info.overridePath(ArtSlot.LOGO))
    }
}
