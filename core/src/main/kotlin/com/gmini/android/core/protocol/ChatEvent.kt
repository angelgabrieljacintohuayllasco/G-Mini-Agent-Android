package com.gmini.android.core.protocol

import kotlinx.serialization.json.JsonObject

/**
 * Evento unificado de un turno de chat. Tanto el WebSocket como el streaming
 * SSE de `POST /api/v1/chat` se traducen a esta misma secuencia, de modo que la
 * interfaz consume una sola fuente sin saber que transporte la alimenta.
 */
public sealed interface ChatEvent {
    public data class Start(val sessionId: String?) : ChatEvent
    public data class Chunk(val text: String) : ChatEvent
    public data class ActionStarted(
        val action: String,
        val params: JsonObject?,
        val id: String?,
    ) : ChatEvent

    public data class ActionFinished(
        val action: String,
        val success: Boolean,
        val message: String?,
        val id: String?,
    ) : ChatEvent

    public data class StateChanged(val status: String, val emotion: String?) : ChatEvent
    public data class Notice(val kind: String?, val text: String) : ChatEvent
    public data class ApprovalRequested(val pending: Boolean, val summary: String?) : ChatEvent
    public data class Done(val sessionId: String?, val reply: String?) : ChatEvent
    public data class Failed(val code: String, val message: String?) : ChatEvent
}

/**
 * Traduce un frame del servidor a un [ChatEvent]. Devuelve `null` para los
 * frames que no forman parte de un turno de chat (notificaciones push,
 * invocaciones de nodo, confirmaciones de registro, `pong`).
 */
public fun WsServerFrame.toChatEvent(): ChatEvent? = when (this) {
    is WsServerFrame.Ready -> ChatEvent.Start(sessionId)
    is WsServerFrame.Chunk -> ChatEvent.Chunk(text)
    is WsServerFrame.Action -> ChatEvent.ActionStarted(action, params, id)
    is WsServerFrame.ActionResult -> ChatEvent.ActionFinished(action, success, message, id)
    is WsServerFrame.State -> ChatEvent.StateChanged(status, emotion)
    is WsServerFrame.Notice -> ChatEvent.Notice(kind, text)
    is WsServerFrame.Approval -> ChatEvent.ApprovalRequested(pending, summary)
    is WsServerFrame.Done -> ChatEvent.Done(sessionId, reply)
    is WsServerFrame.ErrorFrame -> ChatEvent.Failed(code, message)
    is WsServerFrame.Notify -> null
    is WsServerFrame.NodeInvoke -> null
    is WsServerFrame.NodeRegistered -> null
    WsServerFrame.Pong -> null
    is WsServerFrame.Unknown -> null
}
