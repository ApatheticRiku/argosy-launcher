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
            gameDao = mockk(relaxed = true),
            channelDao = mockk(relaxed = true),
            activeSaveRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            apiClient = dagger.Lazy { apiClient },
            engine = mockk(relaxed = true),
            pusher = SnapshotPusher(moshi),
            fileResolver = SnapshotFileResolver()
        )
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
    }

    @Test
    fun `forking creates a labelled channel from the snapshot`() = runBlocking {
        assertEquals(SnapshotActionResult.Done, service.fork(99, 39, "Speedrun"))
        val m = manifest()
        assertEquals("Speedrun", m.getString("label"))
        assertTrue(!m.has("channel_id"))
        assertTrue(m.isNull("expected_current_id"))
        assertEquals(39L, m.getLong("parent_snapshot_id"))
    }

    @Test
    fun `an older client's save becomes a snapshot by copy_of`() = runBlocking {
        assertEquals(SnapshotActionResult.Done, service.makeSnapshot(channel, 1907))
        assertEquals(1907L, manifest().getJSONObject("save").getLong("copy_of"))
    }

    @Test
    fun `a channel moved by another device first reads as stale`() = runBlocking {
        coEvery { api.pushSnapshot("d-1", any()) } returns
            Response.error(409, """{"current":{"id":44,"digest":"sha256:44"},"branch":{"id":51,"digest":"x"}}""".toResponseBody())

        assertEquals(SnapshotActionResult.Stale, service.copyOver(39, channel))
    }
}
