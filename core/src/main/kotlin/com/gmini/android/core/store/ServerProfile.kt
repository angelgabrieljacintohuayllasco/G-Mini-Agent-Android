package com.gmini.android.core.store

import com.gmini.android.core.net.NetworkScope
import kotlinx.serialization.Serializable

/**
 * Perfil de un servidor de G-Mini emparejado. Equivale a los perfiles de la
 * CLI: el usuario puede tener varios y cambiar entre ellos.
 *
 * El token NO vive aqui. Se guarda aparte, en almacenamiento cifrado respaldado
 * por el Keystore de Android, y se referencia por [id].
 */
@Serializable
public data class ServerProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val secure: Boolean = false,
    val deviceId: String? = null,
    val agentName: String? = null,
    val serverName: String? = null,
    val scopes: List<String> = emptyList(),
) {
    /** `true` si el perfil apunta a un servidor fuera de la red local. */
    val isRemote: Boolean get() = NetworkScope.isRemote(host)

    val httpBaseUrl: String
        get() = "${if (secure) "https" else "http"}://$host:$port"

    val webSocketUrl: String
        get() = "${if (secure) "wss" else "ws"}://$host:$port/api/v1/ws"
}
