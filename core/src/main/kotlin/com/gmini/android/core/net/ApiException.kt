package com.gmini.android.core.net

import com.gmini.android.core.GminiJson
import com.gmini.android.core.model.ErrorEnvelope

/**
 * Error devuelto por la API remota. [code] sigue la tabla del contrato
 * (`invalid_token`, `missing_scope`, `busy`, `rate_limited`...), [httpStatus] es
 * el codigo HTTP y [message] el texto en espanol que llega del servidor.
 */
public class ApiException(
    public val code: String,
    override val message: String,
    public val httpStatus: Int,
) : Exception(message) {

    public val isAuthError: Boolean get() = httpStatus == 401 || code == "invalid_token"
    public val isBusy: Boolean get() = httpStatus == 409 || code == "busy"
    public val isMissingScope: Boolean get() = httpStatus == 403 || code == "missing_scope"

    public companion object {
        public fun fromResponse(httpStatus: Int, body: String?): ApiException {
            val parsed = body
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { GminiJson.decodeFromString<ErrorEnvelope>(it).error }.getOrNull() }
            val code = parsed?.code ?: defaultCode(httpStatus)
            val message = parsed?.message ?: defaultMessage(httpStatus, code)
            return ApiException(code, message, httpStatus)
        }

        private fun defaultCode(httpStatus: Int): String = when (httpStatus) {
            400 -> "bad_request"
            401 -> "invalid_token"
            403 -> "missing_scope"
            404 -> "not_found"
            409 -> "busy"
            422 -> "validation_error"
            429 -> "rate_limited"
            503 -> "provider_unavailable"
            else -> "error"
        }

        private fun defaultMessage(httpStatus: Int, code: String): String = when (code) {
            "invalid_token" -> "Token invalido o revocado."
            "missing_scope" -> "El token no tiene permiso para esta accion."
            "not_found" -> "No se encontro el recurso."
            "busy" -> "El agente esta ocupado con otra conversacion."
            "validation_error" -> "La peticion no es valida."
            "rate_limited" -> "Demasiados intentos. Espera un momento."
            "provider_unavailable" -> "El proveedor de IA no esta disponible."
            else -> "Error del servidor ($httpStatus)."
        }
    }
}
