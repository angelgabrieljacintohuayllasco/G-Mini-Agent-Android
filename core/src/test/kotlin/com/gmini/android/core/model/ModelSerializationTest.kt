package com.gmini.android.core.model

import com.gmini.android.core.GminiJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {

    @Test
    fun `el chat omite session_id nulo y adjuntos vacios`() {
        val json = GminiJson.encodeToString(ChatRequest(message = "hola"))
        val obj = GminiJson.parseToJsonElement(json).jsonObject
        assertTrue(obj.containsKey("message"))
        assertFalse("session_id nulo no debe serializarse", obj.containsKey("session_id"))
        assertFalse("attachments vacio no debe serializarse", obj.containsKey("attachments"))
        assertFalse("stream false no debe serializarse", obj.containsKey("stream"))
    }

    @Test
    fun `el chat conserva el session_id new`() {
        val json = GminiJson.encodeToString(ChatRequest(message = "hola", sessionId = "new"))
        assertTrue(json.contains("\"session_id\":\"new\""))
    }

    @Test
    fun `decodifica la respuesta de chat con acciones`() {
        val response = GminiJson.decodeFromString<ChatResponse>(
            """
            {"session_id":"ses_1","reply":"Listo, te aviso a las 18:00.",
             "actions":[{"action":"schedule_create_job","params":{},"success":true,"message":"Tarea creada"}],
             "notices":["Acciones ejecutadas"],"approval_pending":false}
            """.trimIndent(),
        )
        assertEquals("ses_1", response.sessionId)
        assertEquals(1, response.actions.size)
        assertEquals("schedule_create_job", response.actions.first().action)
        assertTrue(response.actions.first().success)
        assertFalse(response.approvalPending)
    }

    @Test
    fun `ignora claves desconocidas por compatibilidad aditiva`() {
        val health = GminiJson.decodeFromString<Health>(
            """{"ok":true,"protocol":1,"version":"0.2.0","mode":"server","name":"G-Mini","requires_auth":true,"extra":"nuevo"}""",
        )
        assertTrue(health.ok)
        assertEquals(1, health.protocol)
        assertEquals("server", health.mode)
    }

    @Test
    fun `decodifica el canje de emparejamiento`() {
        val claim = GminiJson.decodeFromString<PairClaimResponse>(
            """{"token":"gm_dev_abc","device_id":"dev_7f3a","server_name":"tv-server","agent_name":"G-Mini","scopes":["chat","voice","node"]}""",
        )
        assertEquals("gm_dev_abc", claim.token)
        assertEquals("dev_7f3a", claim.deviceId)
        assertEquals(listOf("chat", "voice", "node"), claim.scopes)
    }

    @Test
    fun `la peticion de tarea serializa cron y notify`() {
        val json = GminiJson.encodeToString(
            TaskRequest(
                prompt = "Revisa mi correo",
                title = "Resumen",
                schedule = Schedule(cron = "0 8 * * *", timezone = "America/Lima"),
                notify = listOf("telegram"),
            ),
        )
        assertTrue(json.contains("\"cron\":\"0 8 * * *\""))
        assertTrue(json.contains("\"timezone\":\"America/Lima\""))
        assertTrue(json.contains("\"notify\":[\"telegram\"]"))
    }

    @Test
    fun `los enum de estado y emocion caen en un valor seguro`() {
        assertEquals(AgentStatus.THINKING, AgentStatus.from("thinking"))
        assertEquals(AgentStatus.UNKNOWN, AgentStatus.from("vibrating"))
        assertEquals(AgentEmotion.LOVE, AgentEmotion.from("love"))
        assertEquals(AgentEmotion.NEUTRAL, AgentEmotion.from(null))
    }
}
