package com.gmini.android.core.net

import com.gmini.android.core.GminiJson
import com.gmini.android.core.model.AgentStateDto
import com.gmini.android.core.model.ApprovalDecision
import com.gmini.android.core.model.ChatRequest
import com.gmini.android.core.model.ChatResponse
import com.gmini.android.core.model.DeviceInfo
import com.gmini.android.core.model.DeviceList
import com.gmini.android.core.model.Health
import com.gmini.android.core.model.Me
import com.gmini.android.core.model.MessageList
import com.gmini.android.core.model.PairClaimRequest
import com.gmini.android.core.model.PairClaimResponse
import com.gmini.android.core.model.SessionList
import com.gmini.android.core.model.SessionMessage
import com.gmini.android.core.model.SessionSummary
import com.gmini.android.core.model.SttResponse
import com.gmini.android.core.model.TaskCreated
import com.gmini.android.core.model.TaskInfo
import com.gmini.android.core.model.TaskList
import com.gmini.android.core.model.TaskRequest
import com.gmini.android.core.model.TtsRequest
import com.gmini.android.core.model.VoiceTurnResponse
import com.gmini.android.core.protocol.ChatEvent
import com.gmini.android.core.protocol.SseEventMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

/**
 * Cliente REST de la API remota v1. Adjunta el token Bearer en cada peticion y
 * deja que OkHttp fije la cabecera `Host` a partir de la URL, como exige el
 * contrato.
 */
public class GminiRestClient(
    private val client: OkHttpClient,
    private val streamingClient: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val tokenProvider: () -> String?,
    private val json: Json = GminiJson,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val wavMedia = "audio/wav".toMediaType()

    // --- Salud e identidad -------------------------------------------------

    public suspend fun health(): Health =
        json.decodeFromString(requestString(get("api", "v1", "health").build()))

    public suspend fun me(): Me =
        json.decodeFromString(requestString(authed(get("api", "v1", "me")).build()))

    public suspend fun agentState(): AgentStateDto =
        json.decodeFromString(requestString(authed(get("api", "v1", "agent", "state")).build()))

    // --- Emparejamiento ----------------------------------------------------

    /** Canjea un codigo de 6 digitos. No lleva token (aun no lo hay). */
    public suspend fun pairClaim(request: PairClaimRequest): PairClaimResponse {
        val body = json.encodeToString(request).toRequestBody(jsonMedia)
        val http = post(body, "api", "v1", "pairing", "claim").build()
        return json.decodeFromString(requestString(http))
    }

    public suspend fun listDevices(): List<DeviceInfo> =
        json.decodeFromString<DeviceList>(requestString(authed(get("api", "v1", "devices")).build())).items

    public suspend fun deleteDevice(deviceId: String) {
        requestString(authed(delete("api", "v1", "devices", deviceId)).build())
    }

    // --- Chat --------------------------------------------------------------

    public suspend fun chat(message: String, sessionId: String? = null): ChatResponse {
        val body = json.encodeToString(ChatRequest(message = message, sessionId = sessionId))
            .toRequestBody(jsonMedia)
        return json.decodeFromString(requestString(authed(post(body, "api", "v1", "chat")).build()))
    }

    /** Chat en streaming por SSE. Alternativa al WebSocket. */
    public fun chatStream(message: String, sessionId: String? = null): Flow<ChatEvent> = callbackFlow {
        val payload = json.encodeToString(ChatRequest(message = message, sessionId = sessionId, stream = true))
        val request = authed(post(payload.toRequestBody(jsonMedia), "api", "v1", "chat"))
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                SseEventMapper.toChatEvent(type, data)?.let { trySend(it) }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response != null && !response.isSuccessful) {
                    val errorBody = runCatching { response.body?.string() }.getOrNull()
                    close(ApiException.fromResponse(response.code, errorBody))
                } else {
                    close(t ?: IOException("Se corto el flujo de eventos."))
                }
            }
        }

        val source = EventSources.createFactory(streamingClient).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    /** Aprueba (o cancela) las acciones sensibles pendientes. */
    public suspend fun approve(approve: Boolean) {
        val body = json.encodeToString(ApprovalDecision(approve)).toRequestBody(jsonMedia)
        requestString(authed(post(body, "api", "v1", "approvals")).build())
    }

    // --- Sesiones ----------------------------------------------------------

    public suspend fun listSessions(limit: Int = 20): List<SessionSummary> {
        val http = authed(
            Request.Builder().url(url("api", "v1", "sessions", query = mapOf("limit" to limit.toString()))),
        ).build()
        return json.decodeFromString<SessionList>(requestString(http)).items
    }

    public suspend fun sessionMessages(sessionId: String, limit: Int = 100): List<SessionMessage> {
        val http = authed(
            Request.Builder().url(
                url("api", "v1", "sessions", sessionId, "messages", query = mapOf("limit" to limit.toString())),
            ),
        ).build()
        return json.decodeFromString<MessageList>(requestString(http)).items
    }

    // --- Tareas ------------------------------------------------------------

    public suspend fun createTask(request: TaskRequest): TaskCreated {
        val body = json.encodeToString(request).toRequestBody(jsonMedia)
        return json.decodeFromString(requestString(authed(post(body, "api", "v1", "tasks")).build()))
    }

    public suspend fun getTask(taskId: String): TaskInfo =
        json.decodeFromString(requestString(authed(get("api", "v1", "tasks", taskId)).build()))

    public suspend fun listTasks(status: String? = null): List<TaskInfo> {
        val query = if (status != null) mapOf("status" to status) else emptyMap()
        val http = authed(Request.Builder().url(url("api", "v1", "tasks", query = query))).build()
        return json.decodeFromString<TaskList>(requestString(http)).items
    }

    public suspend fun cancelTask(taskId: String) {
        requestString(authed(delete("api", "v1", "tasks", taskId)).build())
    }

    // --- Voz ---------------------------------------------------------------

    public suspend fun tts(request: TtsRequest): ByteArray {
        val body = json.encodeToString(request).toRequestBody(jsonMedia)
        return requestBytes(authed(post(body, "api", "v1", "voice", "tts")).build())
    }

    public suspend fun stt(wav: ByteArray): SttResponse {
        val http = authed(post(wav.toRequestBody(wavMedia), "api", "v1", "voice", "stt")).build()
        return json.decodeFromString(requestString(http))
    }

    public suspend fun voiceTurn(
        wav: ByteArray,
        sessionId: String? = null,
        replyFormat: String = "wav",
    ): VoiceTurnResponse {
        val query = buildMap {
            if (sessionId != null) put("session_id", sessionId)
            put("reply_format", replyFormat)
        }
        val http = authed(
            Request.Builder()
                .url(url("api", "v1", "voice", "turn", query = query))
                .post(wav.toRequestBody(wavMedia)),
        ).build()
        return json.decodeFromString(requestString(http))
    }

    // --- Infraestructura ---------------------------------------------------

    private fun url(vararg segments: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun get(vararg segments: String): Request.Builder =
        Request.Builder().url(url(*segments)).get()

    private fun delete(vararg segments: String): Request.Builder =
        Request.Builder().url(url(*segments)).delete()

    private fun post(body: okhttp3.RequestBody, vararg segments: String): Request.Builder =
        Request.Builder().url(url(*segments)).post(body)

    private fun authed(builder: Request.Builder): Request.Builder {
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private suspend fun requestString(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException.fromResponse(response.code, body)
            body
        }
    }

    private suspend fun requestBytes(request: Request): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException.fromResponse(response.code, response.body?.string())
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }
}
