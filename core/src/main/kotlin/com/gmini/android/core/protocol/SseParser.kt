package com.gmini.android.core.protocol

import com.gmini.android.core.GminiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Un evento SSE ya separado en su nombre y su carga util. */
public data class SseFrame(val event: String?, val data: String)

/**
 * Analiza el texto de un flujo `text/event-stream`. Se usa sobre todo en las
 * pruebas; en tiempo de ejecucion el cliente delega el troceado de bajo nivel
 * en OkHttp y solo necesita [SseEventMapper].
 */
public object SseParser {
    public fun parse(raw: String): List<SseFrame> {
        val frames = mutableListOf<SseFrame>()
        var event: String? = null
        val data = StringBuilder()

        fun flush() {
            if (event != null || data.isNotEmpty()) {
                frames += SseFrame(event, data.toString().removeSuffix("\n"))
            }
            event = null
            data.setLength(0)
        }

        for (rawLine in raw.split("\n")) {
            val line = rawLine.removeSuffix("\r")
            when {
                line.isEmpty() -> flush()
                line.startsWith(":") -> Unit // comentario SSE: se ignora
                line.startsWith("event:") -> event = line.substringAfter("event:").trim()
                line.startsWith("data:") -> {
                    data.append(line.substringAfter("data:").trim())
                    data.append('\n')
                }
            }
        }
        flush()
        return frames
    }
}

/** Traduce un evento SSE del chat a un [ChatEvent]. */
public object SseEventMapper {

    public fun toChatEvent(event: String?, data: String): ChatEvent? {
        val obj = runCatching { GminiJson.parseToJsonElement(data).jsonObject }.getOrNull()
            ?: return null
        return when (event?.trim()) {
            "start" -> ChatEvent.Start(obj.str("session_id"))
            "chunk" -> ChatEvent.Chunk(obj.str("text").orEmpty())
            "action" -> ChatEvent.ActionStarted(
                action = obj.str("action").orEmpty(),
                params = obj["params"] as? JsonObject,
                id = obj.str("id"),
            )

            "action_result" -> ChatEvent.ActionFinished(
                action = obj.str("action").orEmpty(),
                success = obj.bool("success") ?: false,
                message = obj.str("message"),
                id = obj.str("id"),
            )

            "state" -> ChatEvent.StateChanged(obj.str("status").orEmpty(), obj.str("emotion"))
            "notice" -> ChatEvent.Notice(obj.str("kind"), obj.str("text").orEmpty())
            "approval" -> ChatEvent.ApprovalRequested(
                pending = obj.bool("pending") ?: true,
                summary = obj.str("summary"),
            )

            "done" -> ChatEvent.Done(obj.str("session_id"), obj.str("reply"))
            "error" -> ChatEvent.Failed(
                code = obj.str("code").orEmpty().ifEmpty { "error" },
                message = obj.str("message"),
            )

            else -> null
        }
    }

    public fun toChatEvent(frame: SseFrame): ChatEvent? = toChatEvent(frame.event, frame.data)

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
