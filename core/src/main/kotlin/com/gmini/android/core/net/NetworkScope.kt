package com.gmini.android.core.net

/**
 * Clasifica una direccion como de red local o remota. El cliente solo se
 * conecta a direcciones locales de forma predeterminada; un servidor remoto
 * (una IP de Tailscale, un dominio o una IP publica) exige que el usuario lo
 * agregue a mano en Ajustes y confirme un aviso.
 */
public object NetworkScope {

    /** `true` si [host] apunta al propio equipo o a la red de area local. */
    public fun isLocalNetwork(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        if (h.isEmpty()) return false

        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h.endsWith(".local")) return true // mDNS / Bonjour
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        if (h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return true

        val octets = h.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val a = octets[0].toInt()
            val b = octets[1].toInt()
            return when (a) {
                10 -> true
                127 -> true
                169 -> b == 254 // link-local 169.254.0.0/16
                172 -> b in 16..31
                192 -> b == 168
                else -> false
            }
        }
        return false
    }

    public fun isRemote(host: String): Boolean = !isLocalNetwork(host)
}
