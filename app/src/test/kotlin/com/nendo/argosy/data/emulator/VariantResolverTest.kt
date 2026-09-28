package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VariantResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dao: GameFileDao
    private lateinit var resolver: VariantResolver

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        resolver = VariantResolver(dao)
    }

    private fun onDisk(name: String): String = File(tempFolder.root, name).apply { writeText("x") }.absolutePath

    private fun game(
        platformSlug: String = "psx",
        localPath: String? = "/roms/psx/Game/Game.m3u",
        rommFileName: String? = null,
        activeVariantFileId: Long? = null,
        lastPlayedFileId: Long? = null
    ) = GameEntity(
        id = 1L,
        platformId = 1L,
        platformSlug = platformSlug,
        title = "Test Game",
        sortTitle = "test game",
        localPath = localPath,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED,
        rommFileName = rommFileName,
        activeVariantFileId = activeVariantFileId,
        lastPlayedFileId = lastPlayedFileId
    )

    private fun variant(
        id: Long,
        category: String = "translation",
        fileName: String = "Game (T-En) $id.chd",
        localPath: String? = "/roms/psx/Game/$category/$fileName",
        isLaunchTarget: Boolean = true,
        versionGroup: String? = null
    ) = GameFileEntity(
        id = id,
        gameId = 1L,
        fileName = fileName,
        filePath = localPath ?: "x",
        category = category,
        fileSize = 8L,
        localPath = localPath,
        isLaunchTarget = isLaunchTarget,
        versionGroup = versionGroup
    )

    @Test
    fun `getVariantOptions returns null when there are no variants`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns emptyList()
        assertNull(resolver.getVariantOptions(game()))
    }

    @Test
    fun `getVariantOptions returns null on excluded platforms`() = runTest {
        coEvery { dao.getVariantsForGame(any()) } returns listOf(variant(2L))
        assertNull(resolver.getVariantOptions(game(platformSlug = "switch")))
    }

    @Test
    fun `getVariantOptions offers translation, demo and prototype files after the base game`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            variant(2L, category = "translation"),
            variant(3L, category = "demo"),
            variant(4L, category = "prototype")
        )

        val options = resolver.getVariantOptions(game())!!

        val primary = options.first()
        assertNull("base game option has no fileId", primary.fileId)
        assertEquals("game", primary.category)
        assertEquals(listOf(2L, 3L, 4L), options.drop(1).map { it.fileId })
    }

    @Test
    fun `getVariantOptions never offers hack or mod files, even rows still flagged launchable`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            variant(2L, category = "hack"),
            variant(3L, category = "mod"),
            variant(4L, category = "translation")
        )

        val options = resolver.getVariantOptions(game())!!

        assertEquals(listOf(null, 4L), options.map { it.fileId })
    }

    @Test
    fun `getVariantOptions returns null when only hack and mod files exist`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            variant(2L, category = "hack"),
            variant(3L, category = "mod")
        )

        assertNull(resolver.getVariantOptions(game()))
    }

    @Test
    fun `getVariantOptions never offers the primary file as a variant`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns listOf(
            variant(2L, category = "game", fileName = "Game.chd", versionGroup = "romm:100")
        )

        assertNull(resolver.getVariantOptions(game(rommFileName = "Game.chd")))
    }

    @Test
    fun `getVariantOptions marks the base downloaded state from localPath`() = runTest {
        coEvery { dao.getVariantsForGame(1L) } returns listOf(variant(2L))

        val downloaded = resolver.getVariantOptions(game(localPath = onDisk("Game.m3u")))!!.first()
        assertTrue(downloaded.isDownloaded)

        val notDownloaded = resolver.getVariantOptions(game(localPath = null))!!.first()
        assertFalse(notDownloaded.isDownloaded)
    }

    @Test
    fun `resolveVariant returns the active variant file`() = runTest {
        val active = variant(2L, localPath = onDisk("Translation.chd"))
        coEvery { dao.getById(2L) } returns active

        val resolved = resolver.resolveVariant(game(activeVariantFileId = 2L))

        assertEquals(2L, resolved?.id)
    }

    @Test
    fun `resolveVariant falls back to last played file`() = runTest {
        val last = variant(5L, category = "prototype", localPath = onDisk("Proto.chd"))
        coEvery { dao.getById(5L) } returns last

        val resolved = resolver.resolveVariant(game(lastPlayedFileId = 5L))

        assertEquals(5L, resolved?.id)
    }

    @Test
    fun `resolveVariant ignores a variant whose file is not downloaded`() = runTest {
        coEvery { dao.getById(2L) } returns variant(2L, localPath = null)

        assertNull(resolver.resolveVariant(game(activeVariantFileId = 2L)))
    }

    @Test
    fun `resolveVariant ignores a selected hack or mod file`() = runTest {
        coEvery { dao.getById(2L) } returns variant(2L, category = "hack", localPath = onDisk("Hack.chd"))
        coEvery { dao.getById(3L) } returns variant(3L, category = "mod", localPath = onDisk("Mod.chd"))

        assertNull(resolver.resolveVariant(game(activeVariantFileId = 2L, lastPlayedFileId = 3L)))
    }

    @Test
    fun `resolveVariant ignores a selected uncategorized nested file`() = runTest {
        coEvery { dao.getById(2L) } returns
            variant(2L, category = "unknown", localPath = onDisk("Extra.chd"), isLaunchTarget = false)

        assertNull(resolver.resolveVariant(game(lastPlayedFileId = 2L)))
    }

    @Test
    fun `resolveVariant never turns the primary file into a variant, versionGroup or not`() = runTest {
        val primaryPath = onDisk("Game.chd")
        coEvery { dao.getById(10L) } returns variant(
            10L,
            category = "game",
            fileName = "Game.chd",
            localPath = primaryPath,
            versionGroup = "romm:100"
        )

        assertNull(resolver.resolveVariant(game(localPath = primaryPath, lastPlayedFileId = 10L)))
    }

    @Test
    fun `resolveVariant returns null on excluded platforms even for a version-grouped file`() = runTest {
        coEvery { dao.getById(2L) } returns variant(2L, localPath = onDisk("Other.nsp"), versionGroup = "romm:200")

        assertNull(resolver.resolveVariant(game(platformSlug = "3ds", activeVariantFileId = 2L)))
    }

    @Test
    fun `resolveVariant returns null when nothing is selected`() = runTest {
        assertNull(resolver.resolveVariant(game()))
    }
}
