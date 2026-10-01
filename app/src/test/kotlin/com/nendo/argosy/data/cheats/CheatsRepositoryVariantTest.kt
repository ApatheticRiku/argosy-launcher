package com.nendo.argosy.data.cheats

import android.content.Context
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatVariantTuple
import com.nendo.argosy.data.local.entity.GameEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheatsRepositoryVariantTest {

    private val variants = listOf(
        CheatVariantTuple("USA", "", 698),
        CheatVariantTuple("USA", "Rev 1", 70),
        CheatVariantTuple("Europe", "", 40),
        CheatVariantTuple("Japan", "Rev 0", 17),
        CheatVariantTuple("Japan", "Rev 1", 9)
    )

    private fun build(
        fileName: String?,
        storedRegion: String? = null,
        storedVersion: String? = null
    ): Pair<CheatsRepository, GameDao> {
        val game = mockk<GameEntity> {
            every { rommFileName } returns fileName
            every { localPath } returns null
            every { cheatsSelectedRegion } returns storedRegion
            every { cheatsSelectedVersion } returns storedVersion
        }
        val gameDao = mockk<GameDao>(relaxed = true)
        coEvery { gameDao.getById(GAME_ID) } returns game
        val cheatDao = mockk<CheatDao>(relaxed = true)
        coEvery { cheatDao.getVariantsForGame(GAME_ID) } returns variants
        val repository = CheatsRepository(mockk<Context>(relaxed = true), mockk(relaxed = true), cheatDao, gameDao)
        return repository to gameDao
    }

    @Test
    fun `revision tag selects the matching revision and persists it`() = runTest {
        val (repository, gameDao) = build("Super Mario Bros. 3 (USA) (Rev 1).nes")

        assertEquals("USA" to "Rev 1", repository.resolveSelectedVariant(GAME_ID))
        coVerify { gameDao.updateCheatsSelectedVariant(GAME_ID, "USA", "Rev 1") }
    }

    @Test
    fun `no revision tag selects the unrevised variant`() = runTest {
        val (repository, _) = build("Super Mario Bros. 3 (USA).nes")

        assertEquals("USA" to "", repository.resolveSelectedVariant(GAME_ID))
    }

    @Test
    fun `no exact match leaves the choice to the user`() = runTest {
        val (repository, gameDao) = build("Super Mario Bros. 3 (Japan).nes")

        assertNull(repository.resolveSelectedVariant(GAME_ID))
        coVerify(exactly = 0) { gameDao.updateCheatsSelectedVariant(any(), any(), any()) }
    }

    @Test
    fun `stored choice wins over the file name`() = runTest {
        val (repository, gameDao) = build("Super Mario Bros. 3 (USA) (Rev 1).nes", "Europe", "")

        assertEquals("Europe" to "", repository.resolveSelectedVariant(GAME_ID))
        coVerify(exactly = 0) { gameDao.updateCheatsSelectedVariant(any(), any(), any()) }
    }

    private companion object {
        const val GAME_ID = 8313L
    }
}
