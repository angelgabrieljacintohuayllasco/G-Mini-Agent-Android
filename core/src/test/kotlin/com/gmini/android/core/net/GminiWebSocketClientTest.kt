package com.gmini.android.core.net

import com.gmini.android.core.protocol.WsClientFrame
import com.gmini.android.core.protocol.WsServerFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GminiWebSocketClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `abre el socket, recibe ready y envia hello`() = runBlocking {
        val helloReceived = CountDownLatch(1)
        val helloText = AtomicReference<String>("")

        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    """{"type":"ready","agent_name":"G-Mini","session_id":"ses_1","protocol":1}""",
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                helloText.set(text)
                helloReceived.countDown()
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val client = GminiClient.create(server.url("/").toString(), { "gm_dev_1" })
        val events = CopyOnWriteArrayList<WsEvent>()

        val job = launch(Dispatchers.IO) {
            client.ws.open().collect { event ->
                events.add(event)
                if (event is WsEvent.Open) {
                    event.connection.send(WsClientFrame.Hello("android", "0.1.0", "Pixel"))
                }
            }
        }

        assertTrue("el servidor no recibio el hello", helloReceived.await(5, TimeUnit.SECONDS))
        assertTrue(helloText.get().contains("\"type\":\"hello\""))

        withTimeout(5_000) {
            while (events.none { it is WsEvent.Incoming && it.frame is WsServerFrame.Ready }) {
                delay(25)
            }
        }

        val ready = events.filterIsInstance<WsEvent.Incoming>()
            .map { it.frame }
            .filterIsInstance<WsServerFrame.Ready>()
            .first()
        assertEquals("G-Mini", ready.agentName)
        assertEquals("ses_1", ready.sessionId)

        job.cancel()
    }

    @Test
    fun `un cierre 4401 llega como evento Closed`() = runBlocking {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.close(WS_CLOSE_UNAUTHORIZED, "unauthorized")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val client = GminiClient.create(server.url("/").toString(), { null })
        val events = CopyOnWriteArrayList<WsEvent>()
        val job = launch(Dispatchers.IO) { client.ws.open().collect { events.add(it) } }

        withTimeout(5_000) {
            while (events.none { it is WsEvent.Closed && it.code == WS_CLOSE_UNAUTHORIZED }) {
                delay(25)
            }
        }
        assertTrue(events.any { it is WsEvent.Closed && it.code == WS_CLOSE_UNAUTHORIZED })

        job.cancel()
    }
}
