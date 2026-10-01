package com.nendo.argosy.data.remote.romm

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RomMReachabilityTest {

    private val root = "https://romm.example/"

    private fun answering(code: Int): Call.Factory {
        val request = slot<Request>()
        val factory = mockk<Call.Factory>()
        every { factory.newCall(capture(request)) } answers {
            mockk<Call> {
                every { execute() } returns Response.Builder()
                    .request(request.captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("status $code")
                    .body("".toResponseBody())
                    .build()
            }
        }
        return factory
    }

    private fun failingWith(error: IOException): Call.Factory {
        val factory = mockk<Call.Factory>()
        every { factory.newCall(any()) } returns mockk<Call> {
            every { execute() } throws error
        }
        return factory
    }

    @Test
    fun `a 200 heartbeat reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(200), root))
    }

    @Test
    fun `a 401 heartbeat reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(401), root))
    }

    @Test
    fun `a proxy redirect reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(302), root))
    }

    @Test
    fun `a server error still reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(503), root))
    }

    @Test
    fun `a transport failure reads as unreachable`() {
        assertFalse(heartbeatGotAnyResponse(failingWith(IOException("connection refused")), root))
    }

    @Test
    fun `the probe targets the heartbeat path under the server root`() {
        val request = slot<Request>()
        val factory = mockk<Call.Factory>()
        every { factory.newCall(capture(request)) } returns mockk<Call> {
            every { execute() } throws IOException("timeout")
        }

        heartbeatGotAnyResponse(factory, root)

        assertEquals("https://romm.example/api/heartbeat", request.captured.url.toString())
    }
}
