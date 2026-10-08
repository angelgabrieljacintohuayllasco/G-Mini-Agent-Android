package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version del protocolo remoto que implementa este cliente. */
public const val PROTOCOL_VERSION: Int = 1

/** Puerto por defecto del nucleo de G-Mini. */
public const val DEFAULT_PORT: Int = 8765

/** Respuesta de `GET /api/v1/health` (no requiere autenticacion). */
@Serializable
public data class Health(
    val ok: Boolean = false,
    val protocol: Int = 0,
    val version: String? = null,
    val mode: String? = null,
    val name: String? = null,
    @SerialName("requires_auth") val requiresAuth: Boolean = true,
)

/** Datos del agente que acompanan a la identidad del cliente. */
@Serializable
public data class AgentInfo(
    val name: String? = null,
    val language: String? = null,
    val voice: String? = null,
)

/** Respuesta de `GET /api/v1/me`. */
@Serializable
public data class Me(
    val kind: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    val scopes: List<String> = emptyList(),
    val agent: AgentInfo? = null,
)

/** Cuerpo de error estandar: `{"error": {"code", "message"}}`. */
@Serializable
public data class ErrorEnvelope(
    val error: ErrorBody? = null,
)

@Serializable
public data class ErrorBody(
    val code: String = "bad_request",
    val message: String? = null,
)
