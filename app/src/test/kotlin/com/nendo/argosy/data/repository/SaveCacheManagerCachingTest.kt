package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveCacheManagerCachingTest {

    private lateinit var tempDir: File
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<com.nendo.argosy.data.local.dao.SaveSyncDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncPreferencesRepository = mockk<com.nendo.argosy.data.preferences.SyncPreferencesRepository>(relaxed = true)
    private val savePathResolver = mockk<com.nendo.argosy.data.sync.SavePathResolver>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_caching").toFile()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { saveHandlerRegistry.getFolderHandler(any()) } returns null
        coEvery { saveCacheDao.getByGameAndHash(any(), any(), any()) } returns null
        coEvery { saveCacheDao.insert(any()) } returns 1L

        coEvery { syncPreferencesRepository.isSecureSaves() } returns true

        val fal = realFsFal()
        val archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = gameDao,
            preferencesRepository = preferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            savePathResolver = savePathResolver,
            saveArchiver = archiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            saveOwnershipTracker = mockk(relaxed = true),
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            gciSaveHandler = GciSaveHandler(context, fal, archiver),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `deleting a download keeps versions the server does not hold and removes the rest`() = runTest {
        fun row(id: Long, rommSaveId: Long?, dirty: Boolean) = SaveCacheEntity(
            id = id, gameId = 7L, emulatorId = "retroarch", cachedAt = Instant.parse("2026-10-01T00:00:00Z"),
            saveSize = 3, cachePath = "7/v$id/save.srm", rommSaveId = rommSaveId, needsRemoteSync = dirty
        )
        val held = row(1L, rommSaveId = 40L, dirty = false)
        val localOnly = row(2L, rommSaveId = null, dirty = false)
        val unsent = row(3L, rommSaveId = 41L, dirty = true)
        val cacheRoot = File(tempDir, "save_cache")
        listOf(held, localOnly, unsent).forEach { File(cacheRoot, it.cachePath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) } }
        coEvery { saveCacheDao.getByGame(7L) } returns listOf(held, localOnly, unsent)

        assertEquals(2, manager.deleteServerHeldCachesForGame(7L))
        coVerify { saveCacheDao.deleteByIds(listOf(1L)) }
        assertFalse(File(cacheRoot, held.cachePath).exists())
        assertTrue(File(cacheRoot, localOnly.cachePath).exists())
        assertTrue(File(cacheRoot, unsent.cachePath).exists())
    }

    @Test
    fun `pruning never evicts the active, unsynced, hardcore or server-held version`() = runTest {
        fun row(id: Long, isActive: Boolean = false, dirty: Boolean = false, hardcore: Boolean = false) = SaveCacheEntity(
            id = id, gameId = 1L, emulatorId = "retroarch", cachedAt = java.time.Instant.ofEpochSecond(id),
            saveSize = 1, cachePath = "x/$id.srm", isActive = isActive, needsRemoteSync = dirty, isHardcore = hardcore
        )
        val oldest = listOf(
            row(1, isActive = true), row(2, dirty = true), row(3, hardcore = true),
            row(6).copy(rommSaveId = 60L), row(4), row(5)
        )
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences(saveCacheLimit = 1))
        coEvery { saveCacheDao.getByGameAndOwner(1L, any()) } returns oldest + row(7) + row(8)
        coEvery { saveCacheDao.getOldestUnlockedForOwnerExcluding(1L, any(), any()) } returns oldest
        val deleted = io.mockk.slot<List<Long>>()
        coEvery { saveCacheDao.deleteByIds(capture(deleted)) } returns Unit

        manager.pruneOldCaches(1L, 3L)

        assertEquals(listOf(4L, 5L), deleted.captured)
    }

    @Test
    fun `dedupe keeps the active version and moves its twin's server link and lock onto it`() = runTest {
        val active = SaveCacheEntity(
            id = 10L, gameId = 1L, emulatorId = "retroarch", cachedAt = java.time.Instant.ofEpochSecond(10),
            saveSize = 1, cachePath = "a/save.srm", contentHash = "same", channelName = "autosave", isActive = true
        )
        val lockedTwin = active.copy(id = 11L, cachePath = "b/save.srm", isActive = false, isLocked = true, rommSaveId = 77L)
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(active, lockedTwin)

        manager.dedupeIdenticalCaches(1L)

        coVerify { saveCacheDao.deleteById(11L) }
        coVerify(exactly = 0) { saveCacheDao.deleteById(10L) }
        coVerify { saveCacheDao.updateRommSaveId(10L, 77L) }
        coVerify { saveCacheDao.setLocked(10L, true) }
    }

    @Test
    fun `copying into a named slot locks the new row`() = runTest {
        val captured = copyToChannelCapturing("speedrun")

        assertTrue("A named slot is a user-created slot", captured.isLocked)
    }

    @Test
    fun `copying into the autosave slot leaves the new row unlocked`() = runTest {
        val captured = copyToChannelCapturing(SaveSyncApiClient.AUTOSAVE_SLOT_NAME)

        assertFalse("Autosave is not a user-created slot", captured.isLocked)
    }

    @Test
    fun `copying into a slot leaves other rows in it queued for upload`() = runTest {
        copyToChannelCapturing("speedrun")

        coVerify(exactly = 0) {
            saveCacheDao.clearDirtyFlagForChannel(any(), any(), any(), any())
        }
    }

    @Test
    fun `an archived row holding the same content does not count as a slot`() = runTest {
        val archival = SaveCacheEntity(
            id = 1L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 64L,
            cachePath = "1/archive/game.srm", contentHash = "shared-H",
            channelName = null,
        )
        val named = SaveCacheEntity(
            id = 2L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 64L,
            cachePath = "1/speedrun/game.srm", contentHash = "shared-H",
            channelName = "Speedrun",
        )
        coEvery { saveCacheDao.getAllByGameAndHash(1L, any(), "shared-H") } returns listOf(archival, named)

        val holders = manager.channelsHoldingHash(1L, "shared-H")

        assertEquals(setOf("speedrun"), holders)
    }

    private suspend fun copyToChannelCapturing(targetChannel: String): SaveCacheEntity {
        val sourceDir = File(com.nendo.argosy.util.AppPaths.saveCacheDir(tempDir), "1/20260101-000000")
        sourceDir.mkdirs()
        File(sourceDir, "game.srm").writeBytes(ByteArray(64))
        val source = SaveCacheEntity(
            id = 5L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 64L,
            cachePath = "1/20260101-000000/game.srm", contentHash = "feedface",
            channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
        )
        coEvery { saveCacheDao.getById(5L) } returns source
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 6L

        manager.copyToChannel(5L, targetChannel)

        return captured.captured
    }

    @Test
    fun `caching a file save records the inserted entity with size and hash`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
        )

        assertTrue("Should report Created result", result is SaveCacheManager.CacheResult.Created)
        assertEquals(2048L, captured.captured.saveSize)
        assertTrue("Content hash must be populated", !captured.captured.contentHash.isNullOrBlank())
        assertEquals("retroarch", captured.captured.emulatorId)
        assertEquals(1L, captured.captured.gameId)
    }

    @Test
    fun `caching a file save returns Duplicate when DAO already has the same content hash`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024)) }
        val existing = SaveCacheEntity(
            id = 99L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 1024L,
            cachePath = "1/old/game.srm", contentHash = "deadbeef",
            channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
        )
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(1L, any(), SaveSyncApiClient.AUTOSAVE_SLOT_NAME, any())
        } returns listOf(existing)

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            skipDuplicateCheck = false,
        )

        assertTrue("Duplicate must be reported when hash matches", result is SaveCacheManager.CacheResult.Duplicate)
        val dup = result as SaveCacheManager.CacheResult.Duplicate
        assertEquals(99L, dup.cacheId)
        coVerify(exactly = 0) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `new content is cached even when an older row has the same size and a later timestamp`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024) { 7 }) }
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { saveCacheDao.insert(any()) } returns 8L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        coVerify(exactly = 1) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `a duplicate by hash becomes the active row`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024) { 3 }) }
        coEvery { saveCacheDao.getAllByGameChannelAndHash(1L, any(), "autosave", any()) } returns listOf(
            SaveCacheEntity(
                id = 99L, gameId = 1L, emulatorId = "retroarch",
                cachedAt = Instant.now(), saveSize = 1024L,
                cachePath = "1/old/game.srm", contentHash = "match",
            )
        )

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Duplicate)
        coVerify { saveCacheDao.setActiveRow(1L, any(), 99L) }
    }

    @Test
    fun `skipDuplicateCheck bypasses the hash lookup`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(512)) }

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            skipDuplicateCheck = true,
        )

        assertTrue("Caller asked to skip dedup, must Create", result is SaveCacheManager.CacheResult.Created)
        coVerify(exactly = 1) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `caching a folder save zips contents and stores a zip cache path`() = runTest {
        val folder = File(tempDir, "savedir").apply { mkdirs() }
        File(folder, "a.bin").writeBytes(ByteArray(64))
        File(folder, "b.bin").writeBytes(ByteArray(64))
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "eden", savePath = folder.absolutePath,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        assertTrue("Folder caches must end with .zip", captured.captured.cachePath.endsWith(".zip"))
        val cachedZip = File(tempDir, "save_cache/${captured.captured.cachePath}")
        assertTrue("Cached zip must exist on disk", cachedZip.isFile)
        assertTrue("Cached zip must be non-empty", cachedZip.length() > 0L)
    }

    @Test
    fun `hardcore flag appends trailer to the cached file growing its size`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(256)) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            isHardcore = true,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        assertTrue("Hardcore cache must be flagged on the entity", captured.captured.isHardcore)
        assertTrue("Hardcore cache must be larger than the source (trailer appended)", captured.captured.saveSize > 256L)
    }

    @Test
    fun `precomputedContentHash bypasses the archiver hash call`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024)) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            precomputedContentHash = "precomputed-hash-value",
        )

        assertEquals("precomputed-hash-value", captured.captured.contentHash)
    }

    @Test
    fun `failing save path returns Failed without DAO writes`() = runTest {
        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = "/nonexistent/path.srm",
        )

        assertTrue(result is SaveCacheManager.CacheResult.Failed)
        coVerify(exactly = 0) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `cacheCurrentSave must create a per-channel row when the hash exists in a different channel (audit Bug A4)`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024) { 0x77 }) }
        val existingInCheckpoint = SaveCacheEntity(
            id = 999L,
            gameId = 1L,
            emulatorId = "retroarch",
            cachedAt = Instant.now(),
            saveSize = 1024L,
            cachePath = "1/checkpoint/game.srm",
            channelName = "checkpoint",
            contentHash = "shared-H",
        )
        coEvery { saveCacheDao.getByGameAndHash(1L, any(), any()) } returns existingInCheckpoint

        val result = manager.cacheCurrentSave(
            gameId = 1L,
            emulatorId = "retroarch",
            savePath = save.absolutePath,
            channelName = "manual-clone",
            skipDuplicateCheck = false,
        )

        assertTrue(
            "Cloning a save into a new channel must Create a per-channel row, not be rejected as a cross-channel Duplicate (audit Bug A4): got $result",
            result is SaveCacheManager.CacheResult.Created
        )
        coVerify(exactly = 1) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `cacheAsRollback must create a per-channel row when the hash exists in a different channel (audit Bug A4)`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(2048) { 0x55 }) }
        val existingInChannel = SaveCacheEntity(
            id = 888L,
            gameId = 1L,
            emulatorId = "retroarch",
            cachedAt = Instant.now(),
            saveSize = 2048L,
            cachePath = "1/some-channel/game.srm",
            channelName = "checkpoint",
            contentHash = "shared-H",
        )
        coEvery { saveCacheDao.getByGameAndHash(1L, any(), any()) } returns existingInChannel

        val result = manager.cacheAsRollback(
            gameId = 1L,
            emulatorId = "retroarch",
            savePath = save.absolutePath,
        )

        assertTrue(
            "cacheAsRollback must Create a rollback row even when an identical hash sits in another channel (audit Bug A4): got $result",
            result is SaveCacheManager.CacheResult.Created
        )
    }

    private fun gci(dir: File, name: String, gameCode: String, internal: String, fill: Byte): File =
        File(dir, name).apply {
            val bytes = ByteArray(0x2040) { fill }
            gameCode.toByteArray().copyInto(bytes, 0)
            "8P".toByteArray().copyInto(bytes, 4)
            ByteArray(32).also { internal.toByteArray().copyInto(it) }.copyInto(bytes, 8)
            writeBytes(bytes)
        }

    @Test
    fun `a GameCube save of several files caches every member of the game as one archive`() = runTest {
        val card = File(tempDir, "GC/USA/Card A").apply { mkdirs() }
        val first = gci(card, "8P-GFZE-fzc.dat.gci", "GFZE", "fzc.dat", 1)
        gci(card, "8P-GFZE-f_zero.dat.gci", "GFZE", "f_zero.dat", 2)
        gci(card, "8P-GFZE-ghost1.dat.gci", "GFZE", "ghost1.dat", 3)
        gci(card, "01-GZLE-gczelda.gci", "GZLE", "gczelda", 4)
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(gameId = 1L, emulatorId = "builtin", savePath = first.absolutePath)

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        assertTrue(captured.captured.cachePath.endsWith(".zip"))
        val zip = File(tempDir, "save_cache/${captured.captured.cachePath}")
        val entries = org.apache.commons.compress.archivers.zip.ZipFile(zip).use { z -> z.entries.toList().map { it.name }.sorted() }
        assertEquals(listOf("8P-GFZE-f_zero.dat.gci", "8P-GFZE-fzc.dat.gci", "8P-GFZE-ghost1.dat.gci"), entries)
        assertEquals(captured.captured.contentHash, manager.calculateLocalSaveHash(first.absolutePath, 1L, "builtin"))
    }

    @Test
    fun `a GameCube save of one file caches and hashes the raw file`() = runTest {
        val card = File(tempDir, "GC/USA/Card A").apply { mkdirs() }
        val only = gci(card, "01-GZLE-gczelda.gci", "GZLE", "gczelda", 4)
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        manager.cacheCurrentSave(gameId = 1L, emulatorId = "builtin", savePath = only.absolutePath)

        assertTrue(captured.captured.cachePath.endsWith("01-GZLE-gczelda.gci"))
        assertEquals(SaveArchiver(mockk(relaxed = true), realFsFal()).calculateFileHash(only), captured.captured.contentHash)
    }

    @Test
    fun `a new GameCube file changes the unit hash even when the first file is untouched`() = runTest {
        val card = File(tempDir, "GC/USA/Card A").apply { mkdirs() }
        val first = gci(card, "8P-GFZE-fzc.dat.gci", "GFZE", "fzc.dat", 1)
        gci(card, "8P-GFZE-f_zero.dat.gci", "GFZE", "f_zero.dat", 2)
        val before = manager.calculateLocalSaveHash(first.absolutePath, 1L, "builtin")

        gci(card, "8P-GFZE-ghost1.dat.gci", "GFZE", "ghost1.dat", 3)

        assertFalse(before == manager.calculateLocalSaveHash(first.absolutePath, 1L, "builtin"))
    }
}
