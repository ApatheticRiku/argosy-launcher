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
        engine = SnapshotSyncEngine(
            context = mockk<Context>(relaxed = true),
            gameDao = gameDao,
            channelDao = channelDao,
            sigilSaveHandler = sigil,
            saveCacheManager = dagger.Lazy { mockk(relaxed = true) },
            apiClient = dagger.Lazy { apiClient },
            syncPreferencesRepository = prefs,
            moshi = moshi
        )
    }

    private fun snapshot(id: Long, save: String?) = RomMSnapshot(
        id = id,
        digest = "sha256:$id",
        createdAt = "2026-10-05T10:00:00Z",
        channel = RomMSnapshotChannel(CHANNEL, "Default", romFileId = FILE_ID),
        save = save?.let { RomMSnapshotSave(id * 10, it, it) }
    )

    private fun manifestOf(parts: List<MultipartBody.Part>): JSONObject {
        val buffer = Buffer()
        parts.first().body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `a first save on a file with no channel pushes a new default channel expecting null`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID) } returns null
        coEvery { api.listCurrentSnapshots(listOf(FILE_ID), true) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        val result = engine.sync(GAME_ID, EMULATOR)

        assertEquals(SnapshotSyncResult.Pushed(41), result)
        val manifest = manifestOf(parts.captured)
        assertEquals(FILE_ID, manifest.getLong("rom_file_id"))
        assertEquals("Default", manifest.getString("label"))
        assertTrue(manifest.isNull("expected_current_id"))
        assertEquals("content-a", manifest.getJSONObject("save").getString("hash"))
        assertEquals("neutral", manifest.getJSONObject("save").getString("format"))
        assertTrue("states carry from the parent", !manifest.has("states"))
        assertEquals(41L, stored.captured.heldSnapshotId)
        assertEquals("identity-a", stored.captured.heldSaveIdentityHash)
        assertEquals(manifest.getString("channel_id"), stored.captured.channelId)
    }

    @Test
    fun `a dirty save on top of the held current pushes expecting it`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID) } returns SnapshotChannelEntity(3L, GAME_ID, CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listCurrentSnapshots(listOf(FILE_ID), true) } returns Response.success(listOf(snapshot(41, "old")))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(42), engine.sync(GAME_ID, EMULATOR))
        val manifest = manifestOf(parts.captured)
        assertEquals(41L, manifest.getLong("expected_current_id"))
        assertEquals(CHANNEL, manifest.getString("channel_id"))
        assertTrue(!manifest.has("label"))
    }

    @Test
    fun `another device moving the channel first comes back as a conflict`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID) } returns SnapshotChannelEntity(3L, GAME_ID, CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listCurrentSnapshots(listOf(FILE_ID), true) } returns Response.success(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"current":{"id":43,"digest":"sha256:43"}}""".toResponseBody())

        assertEquals(SnapshotSyncResult.Conflict(null, 43), engine.sync(GAME_ID, EMULATOR))
        coVerify(exactly = 0) { channelDao.upsert(any()) }
    }

    @Test
    fun `holding current with the same save does nothing`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID) } returns SnapshotChannelEntity(3L, GAME_ID, CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listCurrentSnapshots(listOf(FILE_ID), true) } returns Response.success(listOf(snapshot(41, "content-a")))

        assertEquals(SnapshotSyncResult.UpToDate, engine.sync(GAME_ID, EMULATOR))
        coVerify(exactly = 0) { api.pushSnapshot(any(), any()) }
    }

    @Test
    fun `a hardcore current refuses a softcore push until approved`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID) } returns SnapshotChannelEntity(3L, GAME_ID, CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listCurrentSnapshots(listOf(FILE_ID), true) } returns Response.success(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"hardcore_downgrade":true}""".toResponseBody())

        assertEquals(SnapshotSyncResult.HardcoreDowngrade(41), engine.sync(GAME_ID, EMULATOR))
    }

    @Test
    fun `before RomM 5_5 nothing goes through the snapshot API`() = runBlocking {
        every { apiClient.getCapabilities() } returns RomMCapabilities.from("5.4.0")

        assertEquals(SnapshotSyncResult.NotEligible, engine.sync(GAME_ID, EMULATOR))
        coVerify(exactly = 0) { api.listCurrentSnapshots(any(), any()) }
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
