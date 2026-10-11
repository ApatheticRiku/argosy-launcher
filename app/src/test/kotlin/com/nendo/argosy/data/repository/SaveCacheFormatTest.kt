package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SaveCacheFormatTest {

    private lateinit var tempDir: File
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncPreferencesRepository = mockk<com.nendo.argosy.data.preferences.SyncPreferencesRepository>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)
    private val sigil = mockk<SigilSaveHandler>(relaxed = true)
    private val stored = slot<SaveCacheEntity>()

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_format").toFile()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { saveHandlerRegistry.getFolderHandler(any()) } returns null
        coEvery { syncPreferencesRepository.isSecureSaves() } returns true
        coEvery { saveCacheDao.insert(capture(stored)) } returns ROW_ID
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { saveCacheDao.getLatestByGameChannelAndIdentity(any(), any(), any(), any()) } returns null
        notRouted()

        val fal = realFsFal()
        val archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = mockk(relaxed = true),
            preferencesRepository = preferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            savePathResolver = mockk(relaxed = true),
            saveArchiver = archiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            saveOwnershipTracker = mockk(relaxed = true),
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            gciSaveHandler = GciSaveHandler(context, fal, archiver),
            sigilSaveHandler = sigil,
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun notRouted() {
        coEvery { sigil.collect(any(), any(), any()) } returns SigilCollect.NotRouted
        coEvery { sigil.restore(any(), any(), any()) } returns SigilRestore.NotRouted
    }

    private fun routed() {
        coEvery { sigil.collect(any(), any(), any()) } returns
            SigilCollect.Found(byteArrayOf(4, 5, 6), "backup.ram", "unit-hash", "unit-identity")
        coEvery { sigil.restore(any(), any(), any()) } returns SigilRestore.Restored(hardcoreMarker = false)
    }

    private suspend fun cacheLiveSave(): SaveCacheEntity {
        val save = File(tempDir, "saves/Lunar.srm").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64) { 7 }) }
        manager.cacheCurrentSave(gameId = GAME_ID, emulatorId = "retroarch", savePath = save.absolutePath)
        val row = stored.captured.copy(id = ROW_ID)
        coEvery { saveCacheDao.getById(ROW_ID) } returns row
        return row
    }

    @Test
    fun `a save the emulator wrote is cached as native`() = runTest {
        assertEquals(SaveCacheEntity.FORMAT_NATIVE, cacheLiveSave().saveFormat)
    }

    @Test
    fun `a unit Sigil built is cached as neutral`() = runTest {
        routed()

        assertEquals(SaveCacheEntity.FORMAT_NEUTRAL, cacheLiveSave().saveFormat)
    }

    @Test
    fun `a native cache restores the legacy way even once Sigil routes the emulator`() = runTest {
        val row = cacheLiveSave()
        routed()
        val target = File(tempDir, "restored/Lunar.srm")

        manager.restoreSave(ROW_ID, target.absolutePath)

        coVerify(exactly = 0) { sigil.restore(any(), any(), any()) }
        assertArrayEquals(File(tempDir, "save_cache/${row.cachePath}").readBytes(), target.readBytes())
    }

    @Test
    fun `a neutral cache no Sigil layout covers is refused and nothing is written`() = runTest {
        routed()
        cacheLiveSave()
        coEvery { sigil.restore(any(), any(), any()) } returns SigilRestore.NotRouted
        val target = File(tempDir, "restored/backup.ram")

        assertEquals(false, manager.restoreSave(ROW_ID, target.absolutePath))
        assertTrue(!target.exists())
    }

    @Test
    fun `a neutral cache restores through Sigil`() = runTest {
        routed()
        cacheLiveSave()

        assertTrue(manager.restoreSave(ROW_ID, File(tempDir, "restored/backup.ram").absolutePath))

        coVerify(exactly = 1) { sigil.restore(GAME_ID, any(), any()) }
    }

    @Test
    fun `a native cache is never handed to Sigil before a built-in launch`() = runTest {
        val row = cacheLiveSave()
        routed()

        assertEquals(SigilRestore.NotRouted, manager.restoreThroughSigil(row))
        coVerify(exactly = 0) { sigil.restore(any(), any(), any()) }
    }

    @Test
    fun `a row written before formats were recorded still follows the live route`() = runTest {
        val row = cacheLiveSave().copy(saveFormat = null)
        routed()

        assertEquals(SigilRestore.Restored(hardcoreMarker = false), manager.restoreThroughSigil(row))
    }

    @Test
    fun `a copy into another channel keeps the source format`() = runTest {
        routed()
        cacheLiveSave()

        manager.copyToChannel(ROW_ID, "Speedrun")

        assertEquals(SaveCacheEntity.FORMAT_NEUTRAL, stored.captured.saveFormat)
        assertEquals("Speedrun", stored.captured.channelName)
    }

    private companion object {
        const val GAME_ID = 4L
        const val ROW_ID = 31L
    }
}
