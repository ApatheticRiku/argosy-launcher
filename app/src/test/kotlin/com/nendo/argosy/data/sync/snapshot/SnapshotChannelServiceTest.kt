package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.squareup.moshi.Moshi
import io.mockk.coEvery
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

class SnapshotChannelServiceTest {

    private val api = mockk<RomMApi>()
    private val apiClient = mockk<SaveSyncApiClient>()
    private val moshi = Moshi.Builder().build()
    private val parts = slot<List<MultipartBody.Part>>()
    private lateinit var service: SnapshotChannelService

    private val channel = RomMChannel(id = "c-default", label = "default", currentSnapshotId = 42, romFileId = 99)

    @Before
    fun setUp() {
        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns "d-1"
        coEvery { api.pushSnapshot("d-1", capture(parts)) } returns
            Response.success(201, moshi.adapter(RomMSnapshot::class.java).toJson(RomMSnapshot(50, "sha256:50")).toResponseBody())
        service = SnapshotChannelService(
            gameDao = gameDao,
            channelDao = mockk(relaxed = true),
            activeSaveRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            apiClient = dagger.Lazy { apiClient },
            engine = mockk(relaxed = true),
            pusher = SnapshotPusher(moshi),
            fileResolver = SnapshotFileResolver(),
            saveScreenshots = screenshots,
            libraryCache = mockk(relaxed = true)
        )
    }

    private val screenshots = mockk<com.nendo.argosy.hardware.SaveScreenshotCapture>(relaxed = true)
    private val gameDao = mockk<com.nendo.argosy.data.local.dao.GameDao>(relaxed = true)

    private fun stubEmerald() {
        coEvery { gameDao.getById(3) } returns com.nendo.argosy.data.local.entity.GameEntity(
            id = 3, platformId = 1, title = "Emerald", sortTitle = "emerald",
            localPath = "/roms/emerald.gba", rommId = 7, igdbId = null,
            source = com.nendo.argosy.data.model.GameSource.ROMM_SYNCED, platformSlug = "gba"
        )
        val rom = mockk<com.nendo.argosy.data.remote.romm.RomMRom>()
        io.mockk.every { rom.files } returns listOf(
            com.nendo.argosy.data.remote.romm.RomMRomFile(99, 7, "emerald.gba", "p", 1L, "p/emerald.gba")
        )
        coEvery { api.getRom(7) } returns Response.success(rom)
        coEvery { api.listChannels(listOf(99L)) } returns Response.success(listOf(channel.copy(isOwn = true)))
    }

    @Test
    fun `a failed save listing fails the load instead of reading as no saves`() = runBlocking {
        stubEmerald()
        coEvery { api.getSavesByRom(7) } returns Response.error(500, "boom".toResponseBody())

        assertEquals(null, service.load(3))
    }

    @Test
    fun `a refused push reports a reason kind, never the server body`() = runBlocking {
        coEvery { api.pushSnapshot("d-1", any()) } returns Response.error(403, "secret detail".toResponseBody())
        assertEquals(SnapshotActionResult.Failed(SnapshotFailure.REFUSED), service.copyOver(39, channel))

        coEvery { api.pushSnapshot("d-1", any()) } returns Response.error(404, "gone".toResponseBody())
        assertEquals(SnapshotActionResult.Failed(SnapshotFailure.NOT_FOUND), service.copyOver(39, channel))

        coEvery { api.pushSnapshot("d-1", any()) } throws java.io.IOException("no route")
        assertEquals(SnapshotActionResult.Failed(SnapshotFailure.OFFLINE), service.copyOver(39, channel))
    }

    @Test
    fun `a refused rename reports a reason kind`() = runBlocking {
        coEvery { api.updateChannel("c-default", any()) } returns Response.error(422, "bad".toResponseBody())

        assertEquals(SnapshotActionResult.Failed(SnapshotFailure.REFUSED), service.rename("c-default", "Speedrun"))
    }

    @Test
    fun `a channel's own snapshot saves are not listed as older-client saves`() = runBlocking {
        stubEmerald()
        fun save(id: Long, slot: String?, channelId: String?) = com.nendo.argosy.data.remote.romm.RomMSave(
            id = id, romId = 7, userId = 1, emulator = "argosy", fileName = "s$id.srm",
            updatedAt = "2026-10-06T00:00:0${id}Z", slot = slot, channelId = channelId
        )
        coEvery { api.getSavesByRom(7) } returns Response.success(
            listOf(save(1, null, "c-default"), save(2, "autosave", "c-default"), save(3, null, null))
        )

        val library = service.load(3)!!

        assertEquals(listOf(2L), library.mine.single().olderClientSaves.map { it.id })
        assertEquals(listOf(3L), library.backups.map { it.id })
    }

    private fun manifest(): JSONObject {
        assertEquals("a manifest-only push carries no file part", 1, parts.captured.size)
        val buffer = Buffer()
        parts.captured.single().body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `copying a snapshot over a channel names it as parent on top of that channel's current`() = runBlocking {
        assertEquals(SnapshotActionResult.Done, service.copyOver(39, channel))
        val m = manifest()
        assertEquals(99L, m.getLong("rom_file_id"))
        assertEquals("c-default", m.getString("channel_id"))
        assertEquals(42L, m.getLong("expected_current_id"))
        assertEquals(39L, m.getLong("parent_snapshot_id"))
        assertTrue("no save means the parent's save carries", !m.has("save"))
        assertTrue("the source save keeps the emulator that wrote it", !m.has("emulator") && !m.has("core"))
    }

    @Test
    fun `forking creates a labelled channel from the snapshot`() = runBlocking {
        assertEquals(SnapshotActionResult.Done, service.fork(99, 39, "Speedrun"))
        val m = manifest()
        assertEquals("Speedrun", m.getString("label"))
        assertTrue(!m.has("channel_id"))
        assertTrue(m.isNull("expected_current_id"))
        assertEquals(39L, m.getLong("parent_snapshot_id"))
        io.mockk.verify { screenshots.carrySnapshotThumb(39, 50) }
    }

    @Test
    fun `an older client's save becomes a snapshot by copy_of`() = runBlocking {
        assertEquals(SnapshotActionResult.Done, service.makeSnapshot(channel, 1907))
        assertEquals(1907L, manifest().getJSONObject("save").getLong("copy_of"))
        io.mockk.verify(exactly = 0) { screenshots.carrySnapshotThumb(any(), any()) }
    }

    @Test
    fun `a channel moved by another device first reads as stale`() = runBlocking {
        coEvery { api.pushSnapshot("d-1", any()) } returns
            Response.error(409, """{"current":{"id":44,"digest":"sha256:44"},"branch":{"id":51,"digest":"x"}}""".toResponseBody())

        assertEquals(SnapshotActionResult.Stale, service.copyOver(39, channel))
    }
}
