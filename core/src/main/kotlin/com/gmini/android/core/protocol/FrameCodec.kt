package com.gmini.android.core.protocol

import com.gmini.android.core.GminiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Convierte frames del WebSocket a y desde texto JSON. El decodificador nunca
 * lanza por un `type` desconocido: lo devuelve como [WsServerFrame.Unknown],
 * de acuerdo con la regla de que la v1 solo crece de forma aditiva.
 */
public object FrameCodec {

    public fun encode(frame: WsClientFrame): String = encodeToObject(frame).toString()

    public fun encodeToObject(frame: WsClientFrame): JsonObject = when (frame) {
        is WsClientFrame.Hello -> buildJsonObject {
            put("type", "hello")
            put("client", frame.client)
            put("version", frame.version)
            frame.deviceName?.let { put("device_name", it) }
        }

        is WsClientFrame.Chat -> buildJsonObject {
            put("type", "chat")
            put("id", frame.id)
            put("text", frame.text)
            frame.sessionId?.let { put("session_id", it) }
        }

        WsClientFrame.Cancel -> buildJsonObject { put("type", "cancel") }

        WsClientFrame.Ping -> buildJsonObject { put("type", "ping") }

        is WsClientFrame.NodeRegister -> buildJsonObject {
            put("type", "node.register")
            put("platform", frame.platform)
            putJsonArray("surfaces") { frame.surfaces.forEach { add(it) } }
            if (frame.meta.isNotEmpty()) {
                putJsonObject("meta") { frame.meta.forEach { (k, v) -> put(k, v) } }
            }
        }

        is WsClientFrame.NodeResult -> buildJsonObject {
            put("type", "node.result")
            put("request_id", frame.requestId)
            put("ok", frame.ok)
            frame.data?.let { put("data", it) }
            frame.error?.let { put("error", it) }
        }

        is WsClientFrame.NodeEvent -> buildJsonObject {
            put("type", "node.event")
            put("event", frame.event)
            frame.data?.let { put("data", it) }
        }
    }

    public fun decode(text: String): WsServerFrame {
        val obj = GminiJson.parseToJsonElement(text).jsonObject
        return decode(obj)
    }

    public fun decode(obj: JsonObject): WsServerFrame {
        return when (val type = obj.str("type")) {
            "ready" -> WsServerFrame.Ready(
                agentName = obj.str("agent_name"),
                sessionId = obj.str("session_id"),
                protocol = obj.int("protocol"),
            )

            "chunk" -> WsServerFrame.Chunk(id = obj.str("id"), text = obj.str("text").orEmpty())

            "action" -> WsServerFrame.Action(
                action = obj.str("action").orEmpty(),
                params = obj.objOrNull("params"),
                id = obj.str("id"),
            )

            "action_result" -> WsServerFrame.ActionResult(
                action = obj.str("action").orEmpty(),
                success = obj.bool("success") ?: false,
                message = obj.str("message"),
                id = obj.str("id"),
            )

            "node.registered" -> WsServerFrame.NodeRegistered(surfaces = obj.strList("surfaces"))

            "state" -> WsServerFrame.State(
                status = obj.str("status").orEmpty(),
                emotion = obj.str("emotion"),
            )

            "done" -> WsServerFrame.Done(
                id = obj.str("id"),
                reply = obj.str("reply"),
                sessionId = obj.str("session_id"),
            )

            "error" -> WsServerFrame.ErrorFrame(
                code = obj.str("code").orEmpty().ifEmpty { "error" },
                message = obj.str("message"),
            )

            "notify" -> WsServerFrame.Notify(
                title = obj.str("title"),
                body = obj.str("body"),
                priority = obj.str("priority"),
            )

            "node.invoke" -> WsServerFrame.NodeInvoke(
                requestId = obj.str("request_id").orEmpty(),
                surface = obj.str("surface").orEmpty(),
                params = obj.objOrNull("params"),
            )

            "pong" -> WsServerFrame.Pong

            else -> WsServerFrame.Unknown(type = type.orEmpty(), payload = obj)
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.objOrNull(key: String): JsonObject? =
        (this[key] as? JsonObject)

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonElement)?.let { el ->
            runCatching { el.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
        } ?: emptyList()
}
