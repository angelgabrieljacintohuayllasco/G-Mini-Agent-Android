package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Elemento de `GET /api/v1/sessions`. */
@Serializable
public data class SessionSummary(
    val id: String,
    val title: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
)

@Serializable
public data class SessionList(
    val items: List<SessionSummary> = emptyList(),
)

/** Mensaje de `GET /api/v1/sessions/{id}/messages`. */
@Serializable
public data class SessionMessage(
    val role: String,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class MessageList(
    val items: List<SessionMessage> = emptyList(),
)
