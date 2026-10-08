package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Adjunto codificado en base64 que viaja dentro de un mensaje de chat. */
@Serializable
public data class Attachment(
    val name: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("data_base64") val dataBase64: String,
)

/**
 * Cuerpo de `POST /api/v1/chat`.
 *
 * `sessionId` nulo o vacio continua la conversacion actual, `"new"` abre una
 * nueva y el id de una conversacion existente la retoma.
 */
@Serializable
public data class ChatRequest(
    val message: String,
    @SerialName("session_id") val sessionId: String? = null,
    val stream: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
)

/** Resultado de una accion que el agente ejecuto durante el turno. */
@Serializable
public data class ActionInfo(
    val action: String,
    val params: kotlinx.serialization.json.JsonObject? = null,
    val success: Boolean = false,
    val message: String? = null,
    val id: String? = null,
)

/** Respuesta de `POST /api/v1/chat` sin streaming. */
@Serializable
public data class ChatResponse(
    @SerialName("session_id") val sessionId: String? = null,
    val reply: String = "",
    val actions: List<ActionInfo> = emptyList(),
    val notices: List<String> = emptyList(),
    @SerialName("approval_pending") val approvalPending: Boolean = false,
)

/** Cuerpo de `POST /api/v1/approvals`. */
@Serializable
public data class ApprovalDecision(
    val approve: Boolean,
)
