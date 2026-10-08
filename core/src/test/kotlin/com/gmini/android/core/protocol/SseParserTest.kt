package com.gmini.android.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private val stream = buildString {
        append(": keep-alive\n")
        append("event: start\n")
        append("data: {\"session_id\": \"ses_1\"}\n")
        append("\n")
        append("event: chunk\n")
        append("data: {\"text\": \"Listo, \"}\n")
        append("\n")
        append("event: action\n")
        append("data: {\"action\": \"schedule_create_job\", \"params\": {}, \"id\": \"a1\"}\n")
        append("\n")
        append("event: action_result\n")
        append("data: {\"action\": \"schedule_create_job\", \"success\": true, \"message\": \"ok\", \"id\": \"a1\"}\n")
        append("\n")
        append("event: state\n")
        append("data: {\"status\": \"thinking\", \"emotion\": \"neutral\"}\n")
        append("\n")
        append("event: approval\n")
        append("data: {\"pending\": true, \"summary\": \"Ejecutar un comando\"}\n")
        append("\n")
        append("event: done\n")
        append("data: {\"session_id\": \"ses_1\", \"reply\": \"Listo, te aviso a las 18:00.\"}\n")
        append("\n")
    }

    @Test
    fun `trocea el flujo en frames e ignora comentarios`() {
        val frames = SseParser.parse(stream)
        assertEquals(7, frames.size)
        assertEquals("start", frames.first().event)
        assertEquals("done", frames.last().event)
    }

    @Test
    fun `mapea cada evento a su ChatEvent`() {
        val events = SseParser.parse(stream).mapNotNull { SseEventMapper.toChatEvent(it) }

        assertEquals(ChatEvent.Start("ses_1"), events[0])
        assertEquals(ChatEvent.Chunk("Listo, "), events[1])
        assertTrue(events[2] is ChatEvent.ActionStarted)
        assertEquals("schedule_create_job", (events[2] as ChatEvent.ActionStarted).action)
        assertEquals(
            ChatEvent.ActionFinished("schedule_create_job", true, "ok", "a1"),
            events[3],
        )
        assertEquals(ChatEvent.StateChanged("thinking", "neutral"), events[4])
        assertEquals(ChatEvent.ApprovalRequested(true, "Ejecutar un comando"), events[5])
        assertEquals(
            ChatEvent.Done("ses_1", "Listo, te aviso a las 18:00."),
            events[6],
        )
    }

    @Test
    fun `un evento error se mapea a Failed`() {
        val event = SseEventMapper.toChatEvent("error", """{"code":"busy","message":"Ocupado"}""")
        assertEquals(ChatEvent.Failed("busy", "Ocupado"), event)
    }

    @Test
    fun `datos que no son json no rompen el mapeo`() {
        assertNull(SseEventMapper.toChatEvent("chunk", "texto suelto"))
    }

    @Test
    fun `un evento desconocido se ignora`() {
        assertNull(SseEventMapper.toChatEvent("heartbeat", "{}"))
    }
}
