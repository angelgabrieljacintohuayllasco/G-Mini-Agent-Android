package com.gmini.android.core.chat

import com.gmini.android.core.model.AgentStatus
import com.gmini.android.core.protocol.ChatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnReducerTest {

    private fun run(vararg events: ChatEvent): ChatTurnState =
        ChatTurnReducer.reduce(ChatTurnState(), events.toList())

    @Test
    fun `acumula los fragmentos de texto`() {
        val state = run(
            ChatEvent.Start("ses_1"),
            ChatEvent.Chunk("Hola "),
            ChatEvent.Chunk("mundo"),
        )
        assertEquals("ses_1", state.sessionId)
        assertEquals("Hola mundo", state.streamingText)
        assertFalse(state.done)
    }

    @Test
    fun `done fija la respuesta final y el id de sesion`() {
        val state = run(
            ChatEvent.Chunk("Listo, "),
            ChatEvent.Done("ses_9", "Listo, te aviso a las 18:00."),
        )
        assertTrue(state.done)
        assertEquals("Listo, te aviso a las 18:00.", state.reply)
        assertEquals("Listo, te aviso a las 18:00.", state.displayText)
        assertEquals("ses_9", state.sessionId)
        assertEquals(AgentStatus.IDLE, state.status)
    }

    @Test
    fun `done sin reply usa el texto acumulado`() {
        val state = run(ChatEvent.Chunk("Parcial"), ChatEvent.Done("ses_1", null))
        assertEquals("Parcial", state.reply)
    }

    @Test
    fun `las acciones se abren y se cierran por id`() {
        val state = run(
            ChatEvent.ActionStarted("schedule_create_job", null, "a1"),
            ChatEvent.ActionStarted("search_web", null, "a2"),
            ChatEvent.ActionFinished("schedule_create_job", true, "Tarea creada", "a1"),
        )
        assertEquals(2, state.actions.size)
        val first = state.actions.first { it.id == "a1" }
        assertFalse(first.running)
        assertTrue(first.success == true)
        assertEquals("Tarea creada", first.message)
        assertTrue(state.actions.first { it.id == "a2" }.running)
    }

    @Test
    fun `una accion sin id cierra la ultima en curso con el mismo nombre`() {
        val state = run(
            ChatEvent.ActionStarted("run_command", null, null),
            ChatEvent.ActionFinished("run_command", false, "Fallo", null),
        )
        assertEquals(1, state.actions.size)
        assertFalse(state.actions.first().running)
        assertFalse(state.actions.first().success == true)
    }

    @Test
    fun `el estado y la emocion se actualizan`() {
        val state = run(ChatEvent.StateChanged("thinking", "happy"))
        assertEquals(AgentStatus.THINKING, state.status)
        assertEquals(com.gmini.android.core.model.AgentEmotion.HAPPY, state.emotion)
    }

    @Test
    fun `una aprobacion pendiente se guarda y se limpia`() {
        val pending = run(ChatEvent.ApprovalRequested(true, "Ejecutar un comando"))
        assertEquals("Ejecutar un comando", pending.approval?.summary)

        val cleared = ChatTurnReducer.reduce(pending, ChatEvent.ApprovalRequested(false, null))
        assertNull(cleared.approval)
    }

    @Test
    fun `un error corta el turno`() {
        val state = run(ChatEvent.Chunk("..."), ChatEvent.Failed("busy", "Ocupado"))
        assertTrue(state.done)
        assertEquals("busy", state.error?.code)
        assertEquals("Ocupado", state.error?.message)
    }

    @Test
    fun `los avisos se acumulan`() {
        val state = run(ChatEvent.Notice("system", "uno"), ChatEvent.Notice("system", "dos"))
        assertEquals(listOf("uno", "dos"), state.notices)
    }
}
