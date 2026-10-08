package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Respuesta de `GET /api/v1/agent/state`. */
@Serializable
public data class AgentStateDto(
    val status: String = "idle",
    val emotion: String = "neutral",
    val busy: Boolean = false,
)

/**
 * Estados del agente. El campo del protocolo es un texto libre y aditivo, por
 * eso [from] nunca falla: un valor desconocido cae en [UNKNOWN].
 */
public enum class AgentStatus(public val wire: String) {
    IDLE("idle"),
    LISTENING("listening"),
    THINKING("thinking"),
    ACTING("acting"),
    SPEAKING("speaking"),
    UNKNOWN("");

    public companion object {
        public fun from(value: String?): AgentStatus =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: UNKNOWN
    }
}

/** Emociones que el agente usa para animar caras y LEDs. */
public enum class AgentEmotion(public val wire: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    SAD("sad"),
    SURPRISED("surprised"),
    ANGRY("angry"),
    THINKING("thinking"),
    SLEEPY("sleepy"),
    LOVE("love"),
    ERROR("error");

    public companion object {
        public fun from(value: String?): AgentEmotion =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: NEUTRAL
    }
}
