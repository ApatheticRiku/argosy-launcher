package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveCacheManagerRestoreTest {

    private lateinit var tempDir: File
    private lateinit var cacheBaseDir: File
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<com.nendo.argosy.data.local.dao.SaveSyncDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncPreferencesRepository = mockk<com.nendo.argosy.data.preferences.SyncPreferencesRepository>(relaxed = true)
    private val savePathResolver = mockk<com.nendo.argosy.data.sync.SavePathResolver>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_restore_test").toFile()
        cacheBaseDir = File(tempDir, "save_cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        coEvery { syncPreferencesRepository.isSecureSaves() } returns true
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = gameDao,
            preferencesRepository = preferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            savePathResolver = savePathResolver,
            saveArchiver = saveArchiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            saveOwnershipTracker = mockk(relaxed = true),
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            gciSaveHandler = GciSaveHandler(context, fal, saveArchiver),
            sigilSaveHandler = com.nendo.argosy.data.sync.fixtures.notRoutedSigil(),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `restoreSave returns false when unzipSingleFolder fails`() = runTest {
        val cacheFile = File(cacheBaseDir, "save.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        val entity = entity(cachePath = "save.zip")
        coEvery { saveCacheDao.getById(1L) } returns entity
        coEvery { gameDao.getById(any()) } returns null
        every { fal.mkdirs(any()) } returns true
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { saveArchiver.unzipSingleFolder(any(), any()) } returns false

        val ok = manager.restoreSave(cacheId = 1L, targetPath = "/restricted/path/target")

        assertFalse("Restore must report failure when unzip returns false", ok)
        cacheFile.delete()
    }

    @Test
    fun `restoreSave returns false when fal copyFile fails for plain save`() = runTest {
        val cacheFile = File(cacheBaseDir, "save.sav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entity = entity(cachePath = "save.sav")
        coEvery { saveCacheDao.getById(2L) } returns entity
        every { fal.mkdirs(any()) } returns true
        every { saveArchiver.readBytesWithoutTrailer(any()) } returns null
        every { fal.copyFile(any(), any()) } returns false

        val ok = manager.restoreSave(cacheId = 2L, targetPath = "/restricted/path/target.sav")

        assertFalse("Restore must report failure when copyFile returns false", ok)
        cacheFile.delete()
    }

    @Test
    fun `a ps2 cache restore prepares the folder card after unpacking`() = runTest {
        val cacheFile = File(cacheBaseDir, "card.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        coEvery { saveCacheDao.getById(3L) } returns entity(cachePath = "card.zip").copy(emulatorId = "nethersx2")
        coEvery { gameDao.getById(any()) } returns com.nendo.argosy.data.local.entity.GameEntity(
            id = 100L,
            title = "Game",
            sortTitle = "game",
            platformId = 1L,
            platformSlug = "ps2",
            rommId = 7L,
            igdbId = null,
            localPath = null,
            source = com.nendo.argosy.data.model.GameSource.ROMM_SYNCED
        )
        val folderHandler = mockk<com.nendo.argosy.data.sync.platform.FolderSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.getFolderHandler("ps2") } returns folderHandler
        every { fal.mkdirs(any()) } returns true
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { saveArchiver.unzipToFolder(any(), any()) } returns true
        val target = File(tempDir, "memcards/Shared.ps2")

        manager.restoreSave(cacheId = 3L, targetPath = target.absolutePath)

        io.mockk.verify(exactly = 1) { folderHandler.ensureContainerPrepared(target) }
        cacheFile.delete()
    }

    private fun entity(cachePath: String): SaveCacheEntity = SaveCacheEntity(
        id = 0,
        gameId = 100L,
        emulatorId = "eden",
        cachedAt = Instant.now(),
        saveSize = 0,
        cachePath = cachePath,
        contentHash = "deadbeef"
    )
}
