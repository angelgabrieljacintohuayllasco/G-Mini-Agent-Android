package com.gmini.android.core

import kotlinx.serialization.json.Json

/**
 * Instancia de JSON compartida por todo el nucleo del protocolo remoto v1.
 *
 * Los cambios dentro de la v1 son solo aditivos, por eso se ignoran las claves
 * desconocidas: un servidor mas nuevo puede enviar campos que esta version del
 * cliente todavia no modela sin que la deserializacion falle.
 */
public val GminiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = true
    coerceInputValues = true
}
