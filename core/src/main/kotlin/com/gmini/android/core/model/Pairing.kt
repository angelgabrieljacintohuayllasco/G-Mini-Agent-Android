package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cuerpo de `POST /api/v1/pairing/claim` (se envia sin token). */
@Serializable
public data class PairClaimRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_type") val deviceType: String = "android",
    val platform: String = "android",
)

/** Respuesta de un emparejamiento correcto. */
@Serializable
public data class PairClaimResponse(
    val token: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("agent_name") val agentName: String? = null,
    val scopes: List<String> = emptyList(),
)

/** Elemento de `GET /api/v1/devices`. */
@Serializable
public data class DeviceInfo(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    val platform: String? = null,
    val scopes: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
public data class DeviceList(
    val items: List<DeviceInfo> = emptyList(),
)
