package com.gmini.android.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Accion de control del telefono (android-use) que el agente pide como
 * `node.invoke`. Las coordenadas van normalizadas de 0 a 1000 sobre la pantalla
 * (igual que el computer use del escritorio); el servicio las mapea a pixeles.
 */
public sealed interface DeviceAction {
    public data class Tap(val x: Int, val y: Int) : DeviceAction
    public data class Swipe(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Int,
    ) : DeviceAction

    public data class Text(val text: String) : DeviceAction
    public data class Key(val key: String) : DeviceAction
    public data object Screenshot : DeviceAction
}

/**
 * Contrato del control del telefono (modulo opcional, apagado por defecto y
 * detras del servicio de accesibilidad que el usuario activa a mano). Vive en
 * `:core` para validarlo sin Android.
 */
public object DeviceControlContract {
    public const val SURFACE_TAP: String = "device.tap"
    public const val SURFACE_SWIPE: String = "device.swipe"
    public const val SURFACE_TEXT: String = "device.text"
    public const val SURFACE_KEY: String = "device.key"
    public const val SURFACE_SCREENSHOT: String = "device.screenshot"

    public val SURFACES: List<String> = listOf(
        SURFACE_TAP,
        SURFACE_SWIPE,
        SURFACE_TEXT,
        SURFACE_KEY,
        SURFACE_SCREENSHOT,
    )

    public val KEYS: Set<String> = setOf("back", "home", "recents", "notifications")

    private const val MAX_COORD: Int = 1000
    private const val MAX_SWIPE_MS: Int = 10_000

    public fun parse(surface: String, params: JsonObject?): Result<DeviceAction> = when (surface) {
        SURFACE_TAP -> runCatching {
            val x = coord(params, "x")
            val y = coord(params, "y")
            DeviceAction.Tap(x, y)
        }
        SURFACE_SWIPE -> runCatching {
            val duration = params.int("duration_ms") ?: 300
            require(duration in 1..MAX_SWIPE_MS) { "duration_ms fuera de rango." }
            DeviceAction.Swipe(coord(params, "x1"), coord(params, "y1"), coord(params, "x2"), coord(params, "y2"), duration)
        }
        SURFACE_TEXT -> runCatching {
            val text = params?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            require(text.isNotEmpty()) { "El texto esta vacio." }
            DeviceAction.Text(text)
        }
        SURFACE_KEY -> runCatching {
            val key = params?.get("key")?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
            require(key in KEYS) { "Tecla no soportada: $key. Validas: ${KEYS.joinToString()}" }
            DeviceAction.Key(key)
        }
        SURFACE_SCREENSHOT -> Result.success(DeviceAction.Screenshot)
        else -> Result.failure(IllegalArgumentException("Superficie no soportada: $surface"))
    }

    private fun coord(params: JsonObject?, name: String): Int {
        val value = params.int(name) ?: throw IllegalArgumentException("Falta la coordenada $name.")
        require(value in 0..MAX_COORD) { "$name fuera de 0..$MAX_COORD." }
        return value
    }

    private fun JsonObject?.int(name: String): Int? =
        this?.get(name)?.jsonPrimitive?.intOrNull
}
