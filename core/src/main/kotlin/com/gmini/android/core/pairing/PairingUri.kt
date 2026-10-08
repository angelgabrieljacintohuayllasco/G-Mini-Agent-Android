package com.gmini.android.core.pairing

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Datos extraidos de un codigo QR o enlace de emparejamiento. */
public data class PairingTarget(
    val host: String,
    val port: Int,
    val code: String,
)

/** Resultado de analizar un enlace `gmini://pair`. */
public sealed interface PairingUriResult {
    public data class Ok(val target: PairingTarget) : PairingUriResult
    public data class Invalid(val reason: String) : PairingUriResult
}

/**
 * Analiza y construye enlaces `gmini://pair?host=<ip>&port=<port>&code=<6 digitos>`.
 *
 * El esquema `gmini` no es uno de los que entiende `java.net.URL`, por eso el
 * analisis es manual y tolerante con espacios y mayusculas del esquema.
 */
public object PairingUri {
    public const val SCHEME: String = "gmini"
    public const val ACTION: String = "pair"

    private val CODE_REGEX = Regex("^\\d{6}$")
    private const val PREFIX = "$SCHEME://$ACTION"

    public fun parse(raw: String): PairingUriResult {
        val input = raw.trim()
        if (input.isEmpty()) return PairingUriResult.Invalid("El enlace esta vacio.")

        val lower = input.lowercase()
        if (!lower.startsWith("$SCHEME://")) {
            return PairingUriResult.Invalid("El enlace debe empezar con $SCHEME://.")
        }
        // Se acepta gmini://pair, gmini://pair/ y gmini://pair?...
        val afterScheme = input.substring("$SCHEME://".length)
        val authorityAndQuery = afterScheme.substringBefore('#')
        val authorityPath = authorityAndQuery.substringBefore('?')
        val action = authorityPath.trimEnd('/').substringBefore('/')
        if (!action.equals(ACTION, ignoreCase = true)) {
            return PairingUriResult.Invalid("El enlace no es de emparejamiento ($PREFIX).")
        }

        val query = authorityAndQuery.substringAfter('?', "")
        if (query.isEmpty()) {
            return PairingUriResult.Invalid("Faltan los datos de conexion (host, port y code).")
        }
        val params = parseQuery(query)

        val host = params["host"]?.trim().orEmpty()
        if (host.isEmpty()) return PairingUriResult.Invalid("Falta el host del servidor.")

        val portRaw = params["port"]?.trim().orEmpty()
        val port = portRaw.toIntOrNull()
            ?: return PairingUriResult.Invalid("El puerto no es un numero valido.")
        if (port !in 1..65535) return PairingUriResult.Invalid("El puerto esta fuera de rango (1-65535).")

        val code = params["code"]?.trim().orEmpty()
        if (!CODE_REGEX.matches(code)) {
            return PairingUriResult.Invalid("El codigo debe tener exactamente 6 digitos.")
        }

        return PairingUriResult.Ok(PairingTarget(host = host, port = port, code = code))
    }

    public fun build(target: PairingTarget): String =
        "$PREFIX?host=${target.host}&port=${target.port}&code=${target.code}"

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isEmpty()) null else decode(key) to decode(value)
        }.toMap()

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
