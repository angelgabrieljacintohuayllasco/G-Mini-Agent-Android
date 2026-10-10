package com.gmini.android.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Peticion de envio de SMS que el agente manda como `node.invoke` sobre la
 * superficie [SmsContract.SURFACE_SEND]. Es un dato: el modulo de pasarela del
 * telefono solo lo envia tras el consentimiento explicito del usuario.
 */
public data class SmsSendRequest(
    val to: String,
    val text: String,
)

/**
 * Contrato de la pasarela SMS (modulo opcional, apagado por defecto). Vive en
 * `:core` para poder validarlo con pruebas sin depender de Android.
 */
public object SmsContract {
    /** Superficie de nodo que el telefono expone solo cuando la pasarela esta activa. */
    public const val SURFACE_SEND: String = "sms.send"

    /** Evento de nodo con el que el telefono reenvia un SMS entrante al agente. */
    public const val EVENT_RECEIVED: String = "sms.received"

    private const val MAX_TEXT_LENGTH: Int = 1600

    /**
     * Valida y normaliza los parametros de un `sms.send`. Devuelve un error con
     * un mensaje claro si falta el destinatario, no parece un numero o el texto
     * esta vacio o es demasiado largo.
     */
    public fun parseSend(params: JsonObject?): Result<SmsSendRequest> {
        val to = params?.get("to")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val text = params?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (to.isEmpty()) {
            return Result.failure(IllegalArgumentException("Falta el destinatario (to)."))
        }
        if (!looksLikePhoneNumber(to)) {
            return Result.failure(IllegalArgumentException("El destinatario no parece un numero: $to"))
        }
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("El texto del mensaje esta vacio."))
        }
        if (text.length > MAX_TEXT_LENGTH) {
            return Result.failure(IllegalArgumentException("El texto supera el limite de $MAX_TEXT_LENGTH caracteres."))
        }
        return Result.success(SmsSendRequest(to = to, text = text))
    }

    /** `true` si [value] tiene forma de numero telefonico (digitos, con un `+` opcional). */
    public fun looksLikePhoneNumber(value: String): Boolean {
        val cleaned = value.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' || it == '.' }
        if (cleaned.isEmpty()) return false
        val body = if (cleaned.startsWith("+")) cleaned.drop(1) else cleaned
        return body.length in 3..20 && body.all { it.isDigit() }
    }
}
