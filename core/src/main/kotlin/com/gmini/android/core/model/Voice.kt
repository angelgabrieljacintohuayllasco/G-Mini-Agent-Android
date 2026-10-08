package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cuerpo de `POST /api/v1/voice/tts`. */
@Serializable
public data class TtsRequest(
    val text: String,
    val voice: String? = null,
    val format: String = "wav",
)

/** Respuesta de `POST /api/v1/voice/stt`. */
@Serializable
public data class SttResponse(
    val text: String = "",
)

/** Respuesta de `POST /api/v1/voice/turn` (ciclo STT -> agente -> TTS). */
@Serializable
public data class VoiceTurnResponse(
    val transcript: String = "",
    val reply: String = "",
    @SerialName("session_id") val sessionId: String? = null,
    val emotion: String? = null,
    @SerialName("audio_mime") val audioMime: String? = null,
    @SerialName("audio_base64") val audioBase64: String? = null,
)
