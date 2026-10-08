package com.gmini.android.core.protocol

import kotlinx.serialization.json.JsonObject

/** Nombres estandar de superficies de nodo del protocolo v1. */
public object NodeSurfaces {
    public const val DISPLAY_FACE: String = "display.face"
    public const val DISPLAY_TEXT: String = "display.text"
    public const val TTS_SPEAK: String = "tts.speak"
    public const val LED_SET: String = "led.set"
    public const val RELAY_SET: String = "relay.set"
    public const val SENSOR_READ: String = "sensor.read"
    public const val SYSTEM_INFO: String = "system.info"
}

/** Frames que el cliente envia por el WebSocket `/api/v1/ws`. */
public sealed interface WsClientFrame {
    public data class Hello(
        val client: String,
        val version: String,
        val deviceName: String? = null,
    ) : WsClientFrame

    public data class Chat(
        val id: String,
        val text: String,
        val sessionId: String? = null,
    ) : WsClientFrame

    public data object Cancel : WsClientFrame

    public data object Ping : WsClientFrame

    public data class NodeRegister(
        val surfaces: List<String>,
        val platform: String,
        val meta: Map<String, String> = emptyMap(),
    ) : WsClientFrame

    public data class NodeResult(
        val requestId: String,
        val ok: Boolean,
        val data: JsonObject? = null,
        val error: String? = null,
    ) : WsClientFrame

    public data class NodeEvent(
        val event: String,
        val data: JsonObject? = null,
    ) : WsClientFrame
}

/** Frames que el servidor envia por el WebSocket. */
public sealed interface WsServerFrame {
    public data class Ready(
        val agentName: String?,
        val sessionId: String?,
        val protocol: Int?,
    ) : WsServerFrame

    public data class Chunk(val id: String?, val text: String) : WsServerFrame

    public data class Action(
        val action: String,
        val params: JsonObject?,
        val id: String?,
    ) : WsServerFrame

    public data class ActionResult(
        val action: String,
        val success: Boolean,
        val message: String?,
        val id: String?,
    ) : WsServerFrame

    public data class NodeRegistered(val surfaces: List<String>) : WsServerFrame

    public data class State(val status: String, val emotion: String?) : WsServerFrame

    public data class Done(
        val id: String?,
        val reply: String?,
        val sessionId: String?,
    ) : WsServerFrame

    public data class ErrorFrame(val code: String, val message: String?) : WsServerFrame

    public data class Notify(
        val title: String?,
        val body: String?,
        val priority: String?,
    ) : WsServerFrame

    public data class NodeInvoke(
        val requestId: String,
        val surface: String,
        val params: JsonObject?,
    ) : WsServerFrame

    public data object Pong : WsServerFrame

    /** Cualquier `type` que esta version todavia no modela (compatibilidad aditiva). */
    public data class Unknown(val type: String, val payload: JsonObject) : WsServerFrame
}
