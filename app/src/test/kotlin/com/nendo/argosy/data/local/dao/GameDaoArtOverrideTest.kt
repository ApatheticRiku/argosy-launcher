package com.nendo.argosy.data.local.dao

import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.model.ArtSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDaoArtOverrideTest {

    private val gameArtDao = mockk<GameArtDao>(relaxed = true)

    @Test
    fun `clearing an override clears only that slot's override column`() = runTest {
        gameArtDao.clearOverride(7L, ArtSlot.BACKGROUND)

        coVerify(exactly = 1) { gameArtDao.updateOverride(7L, ArtSlot.BACKGROUND.name, null) }
        coVerify(exactly = 0) { gameArtDao.updateCached(any(), any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateSourceUrl(any(), any(), any()) }
    }

    @Test
    fun `forgetting a cached file clears only the cache columns`() = runTest {
        gameArtDao.clearCached(7L, ArtSlot.COVER)

        coVerify(exactly = 1) { gameArtDao.updateCached(7L, ArtSlot.COVER.name, null, null) }
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateSourceUrl(any(), any(), any()) }
    }

    @Test
    fun `resolved art for many games reads each chunk once`() = runTest {
        val ids = (1L..1000L).toList()
        coEvery { gameArtDao.getForGames(any()) } answers {
            firstArg<List<Long>>().map { GameArtEntity(it, ArtSlot.COVER.name, sourceUrl = "u$it") }
        }

        val art = gameArtDao.resolvedFor(ids)

        assertEquals(1000, art.size)
        assertEquals("u500", art[500L]?.coverPath)
        coVerify(exactly = 2) { gameArtDao.getForGames(any()) }
    }

    @Test
    fun `resolved art for no games asks nothing`() = runTest {
        assertTrue(gameArtDao.resolvedFor(emptyList()).isEmpty())
        coVerify(exactly = 0) { gameArtDao.getForGames(any()) }
    }
}
