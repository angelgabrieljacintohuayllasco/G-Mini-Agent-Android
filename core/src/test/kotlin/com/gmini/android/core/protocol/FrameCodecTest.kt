package com.gmini.android.core.protocol

import com.gmini.android.core.GminiJson
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    private fun obj(text: String) = GminiJson.parseToJsonElement(text).jsonObject

    @Test
    fun `codifica el frame hello`() {
        val json = obj(FrameCodec.encode(WsClientFrame.Hello("android", "0.1.0", "Pixel de Gabriel")))
        assertEquals("hello", json["type"]!!.jsonPrimitive.content)
        assertEquals("android", json["client"]!!.jsonPrimitive.content)
        assertEquals("0.1.0", json["version"]!!.jsonPrimitive.content)
        assertEquals("Pixel de Gabriel", json["device_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `codifica el frame chat con session id opcional`() {
        val withSession = obj(FrameCodec.encode(WsClientFrame.Chat("a1", "hola", "ses_1")))
        assertEquals("chat", withSession["type"]!!.jsonPrimitive.content)
        assertEquals("a1", withSession["id"]!!.jsonPrimitive.content)
        assertEquals("ses_1", withSession["session_id"]!!.jsonPrimitive.content)

        val noSession = obj(FrameCodec.encode(WsClientFrame.Chat("a2", "hola")))
        assertNull(noSession["session_id"])
    }

    @Test
    fun `codifica ping y cancel sin campos extra`() {
        assertEquals("ping", obj(FrameCodec.encode(WsClientFrame.Ping))["type"]!!.jsonPrimitive.content)
        assertEquals("cancel", obj(FrameCodec.encode(WsClientFrame.Cancel))["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `codifica node_register con superficies`() {
        val json = obj(
            FrameCodec.encode(
                WsClientFrame.NodeRegister(
                    surfaces = listOf(NodeSurfaces.DISPLAY_TEXT, NodeSurfaces.TTS_SPEAK),
                    platform = "android",
                    meta = mapOf("model" to "Pixel 7"),
                ),
            ),
        )
        assertEquals("node.register", json["type"]!!.jsonPrimitive.content)
        assertEquals("android", json["platform"]!!.jsonPrimitive.content)
        val surfaces = json["surfaces"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("display.text", "tts.speak"), surfaces)
        assertEquals("Pixel 7", json["meta"]!!.jsonObject["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `codifica node_result ok`() {
        val json = obj(FrameCodec.encode(WsClientFrame.NodeResult("req_1", ok = true)))
        assertEquals("node.result", json["type"]!!.jsonPrimitive.content)
        assertEquals("req_1", json["request_id"]!!.jsonPrimitive.content)
        assertTrue(json["ok"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `decodifica ready`() {
        val frame = FrameCodec.decode(
            """{"type":"ready","agent_name":"G-Mini","session_id":"ses_1","protocol":1}""",
        )
        assertEquals(WsServerFrame.Ready("G-Mini", "ses_1", 1), frame)
    }

    @Test
    fun `decodifica chunk y done`() {
        assertEquals(
            WsServerFrame.Chunk("a1", "Listo, "),
            FrameCodec.decode("""{"type":"chunk","id":"a1","text":"Listo, "}"""),
        )
        assertEquals(
            WsServerFrame.Done("a1", "Listo.", "ses_1"),
            FrameCodec.decode("""{"type":"done","id":"a1","reply":"Listo.","session_id":"ses_1"}"""),
        )
    }

    @Test
    fun `decodifica action y action_result`() {
        val action = FrameCodec.decode(
            """{"type":"action","action":"schedule_create_job","params":{"when":"18:00"},"id":"x"}""",
        )
        assertTrue(action is WsServerFrame.Action)
        action as WsServerFrame.Action
        assertEquals("schedule_create_job", action.action)
        assertEquals("18:00", action.params!!["when"]!!.jsonPrimitive.content)

        val result = FrameCodec.decode(
            """{"type":"action_result","action":"schedule_create_job","success":true,"message":"Tarea creada","id":"x"}""",
        )
        assertEquals(
            WsServerFrame.ActionResult("schedule_create_job", true, "Tarea creada", "x"),
            result,
        )
    }

    @Test
    fun `decodifica state notify y node_invoke`() {
        assertEquals(
            WsServerFrame.State("thinking", "neutral"),
            FrameCodec.decode("""{"type":"state","status":"thinking","emotion":"neutral"}"""),
        )
        assertEquals(
            WsServerFrame.Notify("Recordatorio", "Comprar pan", "high"),
            FrameCodec.decode("""{"type":"notify","title":"Recordatorio","body":"Comprar pan","priority":"high"}"""),
        )
        val invoke = FrameCodec.decode(
            """{"type":"node.invoke","request_id":"r1","surface":"tts.speak","params":{"text":"Hola"}}""",
        )
        assertTrue(invoke is WsServerFrame.NodeInvoke)
        invoke as WsServerFrame.NodeInvoke
        assertEquals("r1", invoke.requestId)
        assertEquals("tts.speak", invoke.surface)
        assertEquals("Hola", invoke.params!!["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `decodifica error y pong`() {
        assertEquals(
            WsServerFrame.ErrorFrame("provider_unavailable", "Sin proveedor"),
            FrameCodec.decode("""{"type":"error","code":"provider_unavailable","message":"Sin proveedor"}"""),
        )
        assertEquals(WsServerFrame.Pong, FrameCodec.decode("""{"type":"pong"}"""))
    }

    @Test
    fun `tolera los frames notice y approval`() {
        assertEquals(
            WsServerFrame.Notice("system", "Acciones ejecutadas"),
            FrameCodec.decode("""{"type":"notice","kind":"system","text":"Acciones ejecutadas"}"""),
        )
        assertEquals(
            WsServerFrame.Approval(true, "Ejecutar un comando"),
            FrameCodec.decode("""{"type":"approval","pending":true,"summary":"Ejecutar un comando"}"""),
        )
    }

    @Test
    fun `un type desconocido no rompe la decodificacion`() {
        val frame = FrameCodec.decode("""{"type":"telemetry","cpu":42}""")
        assertTrue(frame is WsServerFrame.Unknown)
        assertEquals("telemetry", (frame as WsServerFrame.Unknown).type)
        assertEquals(42, frame.payload["cpu"]!!.jsonPrimitive.int)
    }

    @Test
    fun `los frames del servidor se traducen a eventos de chat`() {
        assertEquals(ChatEvent.Chunk("hola"), WsServerFrame.Chunk("a", "hola").toChatEvent())
        assertEquals(ChatEvent.Done("ses", "fin"), WsServerFrame.Done("a", "fin", "ses").toChatEvent())
        assertNull(WsServerFrame.Pong.toChatEvent())
        assertNull(WsServerFrame.Notify("t", "b", null).toChatEvent())
        assertNull(WsServerFrame.NodeInvoke("r", "tts.speak", null).toChatEvent())
    }
}
