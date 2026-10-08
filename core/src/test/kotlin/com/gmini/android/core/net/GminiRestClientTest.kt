package com.gmini.android.core.net

import com.gmini.android.core.model.PairClaimRequest
import com.gmini.android.core.model.TaskRequest
import com.gmini.android.core.protocol.ChatEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GminiRestClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GminiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GminiClient.create(server.url("/").toString(), { "gm_dev_1" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health no envia token`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"protocol":1,"version":"0.2.0","mode":"server","name":"G-Mini","requires_auth":true}""",
            ),
        )
        val health = client.rest.health()
        assertTrue(health.ok)
        assertEquals("G-Mini", health.name)

        val request = server.takeRequest()
        assertEquals("/api/v1/health", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `me envia el token bearer`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"kind":"device","device_id":"dev_1","device_name":"Pixel","scopes":["chat"],"agent":{"name":"G-Mini","language":"es"}}""",
            ),
        )
        val me = client.rest.me()
        assertEquals("dev_1", me.deviceId)

        val request = server.takeRequest()
        assertEquals("/api/v1/me", request.path)
        assertEquals("Bearer gm_dev_1", request.getHeader("Authorization"))
    }

    @Test
    fun `pairClaim hace POST sin token y con el codigo`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"token":"gm_dev_new","device_id":"dev_9","server_name":"tv-server","agent_name":"G-Mini","scopes":["chat","voice"]}""",
            ),
        )
        val response = client.rest.pairClaim(
            PairClaimRequest(code = "482913", deviceName = "Pixel de Gabriel"),
        )
        assertEquals("gm_dev_new", response.token)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pairing/claim", request.path)
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("482913"))
    }

    @Test
    fun `chat sin streaming devuelve la respuesta`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"session_id":"ses_1","reply":"Listo.","actions":[],"notices":[],"approval_pending":false}""",
            ),
        )
        val response = client.rest.chat("Recuerdame comprar pan")
        assertEquals("ses_1", response.sessionId)
        assertEquals("Listo.", response.reply)

        val request = server.takeRequest()
        assertEquals("/api/v1/chat", request.path)
        assertTrue(request.body.readUtf8().contains("comprar pan"))
    }

    @Test
    fun `un 401 se traduce a ApiException de autenticacion`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"invalid_token","message":"Token invalido o revocado"}}""",
            ),
        )
        try {
            client.rest.me()
            fail("Se esperaba ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isAuthError)
            assertEquals("invalid_token", e.code)
            assertEquals("Token invalido o revocado", e.message)
        }
    }

    @Test
    fun `createTask devuelve el id y el estado`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setBody("""{"task_id":"tsk_1","status":"scheduled"}"""),
        )
        val created = client.rest.createTask(
            TaskRequest(prompt = "Resumen de correo", title = "Correo"),
        )
        assertEquals("tsk_1", created.taskId)
        assertEquals("scheduled", created.status)
    }

    @Test
    fun `deleteDevice usa el metodo DELETE`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        client.rest.deleteDevice("dev_9")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/devices/dev_9", request.path)
    }

    @Test
    fun `chatStream entrega los eventos SSE en orden`() = runBlocking {
        val sse = buildString {
            append("event: start\ndata: {\"session_id\":\"ses_1\"}\n\n")
            append("event: chunk\ndata: {\"text\":\"Hola \"}\n\n")
            append("event: chunk\ndata: {\"text\":\"mundo\"}\n\n")
            append("event: done\ndata: {\"session_id\":\"ses_1\",\"reply\":\"Hola mundo\"}\n\n")
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )

        val events = withTimeout(10_000) {
            client.rest.chatStream("saluda").toList()
        }

        assertEquals(ChatEvent.Start("ses_1"), events.first())
        assertEquals(ChatEvent.Chunk("Hola "), events[1])
        assertEquals(ChatEvent.Chunk("mundo"), events[2])
        assertEquals(ChatEvent.Done("ses_1", "Hola mundo"), events.last())

        val request = server.takeRequest()
        assertEquals("/api/v1/chat", request.path)
        assertEquals("text/event-stream", request.getHeader("Accept"))
    }
}
