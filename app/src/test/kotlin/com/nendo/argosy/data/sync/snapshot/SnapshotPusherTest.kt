package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class SnapshotPusherTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val api = mockk<RomMApi>()
    private val moshi = Moshi.Builder().build()
    private val pusher = SnapshotPusher(moshi)
    private val sent = mutableListOf<List<MultipartBody.Part>>()

    private val unit = SnapshotUnit(ByteArray(8), "save.sram", "save-hash", "save-hash", "SINGLE", "neutral")

    private fun written() =
        Response.success(201, moshi.adapter(RomMSnapshot::class.java).toJson(RomMSnapshot(50, "sha256:50")).toResponseBody())

    private fun error(code: Int, body: String): Response<okhttp3.ResponseBody> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    private fun answer(vararg responses: Response<okhttp3.ResponseBody>) {
        val queue = ArrayDeque(responses.toList())
        coEvery { api.pushSnapshot(DEVICE, any()) } coAnswers {
            sent += secondArg<List<MultipartBody.Part>>()
            queue.removeFirst()
        }
    }

    private fun names(parts: List<MultipartBody.Part>) =
        parts.map { it.headers?.get("Content-Disposition")?.substringAfter("name=\"")?.substringBefore('"') }

    private fun manifestOf(parts: List<MultipartBody.Part>): JSONObject {
        val buffer = Buffer()
        parts.first().body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun state(hash: String, serverHasIt: Boolean) = StatePart(
        core = "snes9x",
        slot = "auto",
        file = tempDir.newFile().apply { writeBytes(ByteArray(4)) },
        hash = hash,
        screenshot = null,
        serverHasIt = serverHasIt
    )

    @Test
    fun `a save the server holds travels as its hash alone`() = runBlocking {
        answer(written())

        val outcome = pusher.push(api, DEVICE, JSONObject(), unit, saveServerHasIt = true)

        assertTrue(outcome is PushOutcome.Written)
        assertEquals(listOf("manifest"), names(sent.single()))
    }

    @Test
    fun `parts the server reports missing are sent on one retry`() = runBlocking {
        answer(error(400, """{"missing":["save","state:snes9x:auto"]}"""), written())

        val outcome = pusher.push(
            api, DEVICE, JSONObject(), unit, states = listOf(state("a1", serverHasIt = true)), saveServerHasIt = true
        )

        assertTrue(outcome is PushOutcome.Written)
        assertEquals(listOf("manifest"), names(sent[0]))
        assertEquals(listOf("manifest", "save", "state:snes9x:auto"), names(sent[1]))
    }

    @Test
    fun `a pruned parent is dropped and the push sent again`() = runBlocking {
        answer(error(404, """{"detail":"Parent snapshot not found"}"""), written())
        val manifest = JSONObject().put("parent_snapshot_id", 39).put("expected_current_id", 42)

        val outcome = pusher.push(api, DEVICE, manifest)

        assertTrue(outcome is PushOutcome.Written)
        assertTrue(manifestOf(sent[0]).has("parent_snapshot_id"))
        assertFalse(manifestOf(sent[1]).has("parent_snapshot_id"))
        assertEquals(42, manifestOf(sent[1]).getInt("expected_current_id"))
    }

    @Test
    fun `any other not-found fails without a retry`() = runBlocking {
        answer(error(404, """{"detail":"Channel not found"}"""))

        val outcome = pusher.push(api, DEVICE, JSONObject().put("parent_snapshot_id", 39))

        assertEquals(SnapshotFailure.NOT_FOUND, (outcome as PushOutcome.Failed).failure)
        assertEquals(1, sent.size)
    }

    private companion object {
        const val DEVICE = "d-1"
    }
}
