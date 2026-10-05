package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SnapshotChannelDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRom
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotChannel
import com.nendo.argosy.data.remote.romm.RomMSnapshotSave
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRoute
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SnapshotSyncEngineTest {

    private val api = mockk<RomMApi>()
    private val apiClient = mockk<SaveSyncApiClient>()
    private val gameDao = mockk<GameDao>()
    private val channelDao = mockk<SnapshotChannelDao>(relaxed = true)
    private val sigil = mockk<SigilSaveHandler>()
    private val prefs = mockk<SyncPreferencesRepository>()
    private val moshi = Moshi.Builder().build()
    private val snapshotJson = moshi.adapter(RomMSnapshot::class.java)

    private val game = GameEntity(
        id = GAME_ID,
        title = "Lunar",
        sortTitle = "lunar",
        platformId = 1L,
        platformSlug = "scd",
        rommId = ROMM_ID,
        igdbId = null,
        localPath = "/roms/scd/Lunar (USA).cue",
        source = GameSource.ROMM_SYNCED
    )
    private val local = SigilCollect.Found(byteArrayOf(1, 2, 3), "backup.ram", "content-a", "identity-a")
    private val stored = slot<SnapshotChannelEntity>()

    private lateinit var engine: SnapshotSyncEngine

    @Before
    fun setUp() {
        every { apiClient.getCapabilities() } returns RomMCapabilities.from("5.5.0")
        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns DEVICE
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { prefs.getRommUserId() } returns 3L
        coEvery { sigil.route(GAME_ID, EMULATOR) } returns SigilRoute("genesis_plus_gx", "/saves", null, null)
        coEvery { sigil.collect(GAME_ID, EMULATOR, any()) } returns local
        val rom = mockk<RomMRom>()
        every { rom.files } returns listOf(
            RomMRomFile(FILE_ID, ROMM_ID, "Lunar (USA).cue", "p", 1L, "p/Lunar (USA).cue"),
            RomMRomFile(FILE_ID + 1, ROMM_ID, "Lunar (USA) (Track 1).bin", "p", 1L, "p/t1")
        )
        coEvery { api.getRom(ROMM_ID) } returns Response.success(rom)
        coEvery { channelDao.upsert(capture(stored)) } returns Unit
        every { context.cacheDir } returns tempDir.root
        every { archiver.hasHardcoreTrailer(any()) } returns false
        every { archiver.readBytesWithoutTrailer(any()) } returns null
        every { archiver.calculateContentHash(any()) } answers { "md5-of-" + firstArg<java.io.File>().readBytes().size }
        engine = SnapshotSyncEngine(
            context = context,
            gameDao = gameDao,
            channelDao = channelDao,
            saveCacheDao = mockk(relaxed = true),
            sigilSaveHandler = sigil,
            saveCacheManager = dagger.Lazy { cacheManager },
            saveDownloader = dagger.Lazy { downloader },
            apiClient = dagger.Lazy { apiClient },
            emulatorResolver = mockk(relaxed = true),
            saveArchiver = archiver,
            syncPreferencesRepository = prefs,
            pusher = SnapshotPusher(moshi),
            fileResolver = SnapshotFileResolver()
        )
    }

    @get:org.junit.Rule
    val tempDir = org.junit.rules.TemporaryFolder()
    private val context = mockk<Context>(relaxed = true)
    private val archiver = mockk<com.nendo.argosy.data.sync.SaveArchiver>()
    private val cacheManager = mockk<com.nendo.argosy.data.repository.SaveCacheManager>(relaxed = true)
    private val downloader = mockk<com.nendo.argosy.data.repository.SaveDownloader>()

    private fun snapshot(id: Long, save: String?) = RomMSnapshot(
        id = id,
        digest = "sha256:$id",
        createdAt = "2026-10-05T10:00:00Z",
        channel = RomMSnapshotChannel(CHANNEL, "default", romFileId = FILE_ID),
        save = save?.let { RomMSnapshotSave(id * 10, it, it) }
    )

    private fun manifestOf(parts: List<MultipartBody.Part>): JSONObject {
        val buffer = Buffer()
        parts.first().body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `a first save on a file with no channel pushes a new default channel expecting null`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        val result = engine.sync(GAME_ID, EMULATOR, null)

        assertEquals(SnapshotSyncResult.Pushed(41), result)
        val manifest = manifestOf(parts.captured)
        assertEquals(FILE_ID, manifest.getLong("rom_file_id"))
        assertEquals("default", manifest.getString("label"))
        assertTrue(manifest.isNull("expected_current_id"))
        assertEquals("content-a", manifest.getJSONObject("save").getString("hash"))
        assertEquals("neutral", manifest.getJSONObject("save").getString("format"))
        assertTrue("states carry from the parent", !manifest.has("states"))
        assertEquals(41L, stored.captured.heldSnapshotId)
        assertEquals("identity-a", stored.captured.heldSaveIdentityHash)
        assertEquals(manifest.getString("channel_id"), stored.captured.channelId)
    }

    private fun channelsOf(snapshots: List<RomMSnapshot>): Response<List<RomMChannel>> =
        Response.success(snapshots.map { snap ->
            RomMChannel(id = snap.channel!!.id, label = snap.channel!!.label, currentSnapshotId = snap.id, romFileId = FILE_ID, current = snap)
        })

    @Test
    fun `a first push joins the file's empty default channel instead of creating one`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(listOf(
            RomMChannel(id = "migrated", label = "default", romFileId = FILE_ID),
            RomMChannel(id = "speedrun", label = "Speedrun", romFileId = FILE_ID),
            RomMChannel(id = "someone-else", label = "default", isOwn = false, isPublic = true, romFileId = FILE_ID)
        ))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(41), engine.sync(GAME_ID, EMULATOR, null))
        val manifest = manifestOf(parts.captured)
        assertEquals("migrated", manifest.getString("channel_id"))
        assertTrue("an existing channel takes no label", !manifest.has("label"))
        assertTrue(manifest.isNull("expected_current_id"))
    }

    @Test
    fun `a dirty save on top of the held current pushes expecting it`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(42), engine.sync(GAME_ID, EMULATOR, null))
        val manifest = manifestOf(parts.captured)
        assertEquals(41L, manifest.getLong("expected_current_id"))
        assertEquals(CHANNEL, manifest.getString("channel_id"))
        assertTrue(!manifest.has("label"))
    }

    @Test
    fun `another device moving the channel first comes back as a conflict`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"current":{"id":43,"digest":"sha256:43"}}""".toResponseBody())

        assertEquals(SnapshotSyncResult.Conflict(null, 43), engine.sync(GAME_ID, EMULATOR, null))
        coVerify(exactly = 0) { channelDao.upsert(any()) }
    }

    @Test
    fun `holding current with the same save does nothing`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "content-a")))

        assertEquals(SnapshotSyncResult.UpToDate, engine.sync(GAME_ID, EMULATOR, null))
        coVerify(exactly = 0) { api.pushSnapshot(any(), any()) }
    }

    @Test
    fun `a hardcore current refuses a softcore push until approved`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"hardcore_downgrade":true}""".toResponseBody())

        assertEquals(SnapshotSyncResult.HardcoreDowngrade(41), engine.sync(GAME_ID, EMULATOR, null))
    }

    @Test
    fun `an offline chain pushes each cached save on top of the one before`(): Unit = runBlocking {
        val heldAfterFirst = SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 44, "d", "c1", "c1", 0)
        coEvery { channelDao.get(3L, GAME_ID, "default") } returnsMany listOf(
            SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old", 0),
            heldAfterFirst
        )
        coEvery { api.listChannels(listOf(FILE_ID)) } returnsMany listOf(
            channelsOf(listOf(snapshot(43, "theirs"))),
            channelsOf(listOf(snapshot(44, "c1")))
        )
        val pushes = mutableListOf<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(pushes)) } returnsMany listOf(
            Response.success(201, snapshotJson.toJson(snapshot(44, "c1")).toResponseBody()),
            Response.success(201, snapshotJson.toJson(snapshot(45, "c2")).toResponseBody())
        )
        val unit = kotlin.io.path.createTempFile(suffix = ".ram").toFile().apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, unit, onTopOfCurrent = true)
        engine.pushCached(GAME_ID, EMULATOR, null, unit, onTopOfCurrent = false)

        assertEquals(43L, manifestOf(pushes[0]).getLong("expected_current_id"))
        assertEquals(44L, manifestOf(pushes[1]).getLong("expected_current_id"))
        unit.delete()
    }

    @Test
    fun `a cached unit's shape is read from its bytes`() {
        fun zip(vararg names: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip -> names.forEach { zip.putNextEntry(java.util.zip.ZipEntry(it)); zip.write(1); zip.closeEntry() } }
            return out.toByteArray()
        }
        assertEquals("SINGLE", SigilSaveHandler.unitShape(null, byteArrayOf(1, 2, 3)))
        assertEquals("MULTI", SigilSaveHandler.unitShape(null, zip("backup.ram", "cart.ram")))
        assertEquals("FOLDER", SigilSaveHandler.unitShape(null, zip("0100A/a.bin", "0100A/b.bin")))
        assertEquals("FOLDER", SigilSaveHandler.unitShape(null, zip("0100A/a.bin", "device/0100A/b.bin")))
    }

    private fun stubNativeSave(bytes: ByteArray) {
        coEvery { sigil.collect(GAME_ID, EMULATOR, any()) } returns SigilCollect.NotRouted
        coEvery { sigil.route(GAME_ID, EMULATOR) } returns null
        coEvery { apiClient.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns "/saves/Emerald.srm"
        coEvery { apiClient.resolveCoreForGame(any<GameEntity>(), any()) } returns "mgba"
        coEvery { cacheManager.calculateLocalSaveHash("/saves/Emerald.srm", GAME_ID, EMULATOR) } returns "disk-hash"
        coEvery {
            cacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns com.nendo.argosy.data.repository.SaveCacheManager.CacheResult.Created(1L, 7L)
        val entity = mockk<com.nendo.argosy.data.local.entity.SaveCacheEntity>()
        coEvery { cacheManager.getCacheById(7L) } returns entity
        val cached = tempDir.newFile("Emerald.srm").apply { writeBytes(bytes) }
        every { cacheManager.getCacheFile(entity) } returns cached
    }

    @Test
    fun `a save Sigil doesn't route pushes as the emulator wrote it, labelled native`() = runBlocking {
        stubNativeSave(ByteArray(131072))
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "md5-of-131072")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(41), engine.sync(GAME_ID, EMULATOR, null))
        val save = manifestOf(parts.captured).getJSONObject("save")
        assertEquals("native", save.getString("format"))
        assertEquals("SINGLE", save.getString("shape"))
        assertEquals("md5-of-131072", save.getString("hash"))
        assertEquals("Emerald.srm", parts.captured[1].headers?.get("Content-Disposition")?.substringAfter("filename=\"")?.substringBefore('"'))
    }

    @Test
    fun `a named Argosy channel syncs as the spec channel with its label`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "Speedrun") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "other")))
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(50, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(50), engine.sync(GAME_ID, EMULATOR, "Speedrun"))
        val manifest = manifestOf(parts.captured)
        assertEquals("Speedrun", manifest.getString("label"))
        assertTrue("the default channel's current is not this channel's", manifest.isNull("expected_current_id"))
        assertEquals("Speedrun", stored.captured.label)
    }

    @Test
    fun `a newer current on a clean device is placed by the legacy downloader and reported`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(42) } returns Response.success(snapshot(42, "theirs"))
        coEvery {
            downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true)
        } returns com.nendo.argosy.data.repository.SaveSyncResult.Success()
        coEvery { api.reportSnapshotHeld(42, DEVICE) } returns Response.success(Unit)

        assertEquals(SnapshotSyncResult.Applied(42), engine.sync(GAME_ID, EMULATOR, null))
        assertEquals(42L, stored.captured.heldSnapshotId)
        coVerify { api.reportSnapshotHeld(42, DEVICE) }
    }

    @Test
    fun `before RomM 5_5 nothing goes through the snapshot API`() = runBlocking {
        every { apiClient.getCapabilities() } returns RomMCapabilities.from("5.4.0")

        assertEquals(SnapshotSyncResult.NotEligible, engine.sync(GAME_ID, EMULATOR, null))
        coVerify(exactly = 0) { api.listChannels(any()) }
    }

    private companion object {
        const val GAME_ID = 5L
        const val ROMM_ID = 70L
        const val FILE_ID = 99L
        const val DEVICE = "d-7f3a"
        const val EMULATOR = "argosy"
        const val CHANNEL = "0192f1c4-0000-7000-8000-000000000000"
    }
}
