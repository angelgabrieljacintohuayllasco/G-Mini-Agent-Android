package com.gmini.android.core.net

import com.gmini.android.core.store.ServerProfile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Agrupa el cliente REST y el WebSocket de un mismo servidor. Comparte el pool
 * de conexiones de OkHttp y usa tiempos de espera distintos para las peticiones
 * normales, el streaming SSE y el WebSocket de larga duracion.
 */
public class GminiClient private constructor(
    public val baseUrl: HttpUrl,
    public val rest: GminiRestClient,
    public val ws: GminiWebSocketClient,
) {
    public companion object {
        public fun baseHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        public fun create(
            baseUrl: String,
            tokenProvider: () -> String?,
            base: OkHttpClient = baseHttpClient(),
        ): GminiClient {
            val url = baseUrl.toHttpUrl()

            // El SSE y el WebSocket viven mucho mas que una peticion normal:
            // se les quita el limite de lectura y se activa el ping de keepalive.
            val longLived = base.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            val socket = longLived.newBuilder()
                .pingInterval(25, TimeUnit.SECONDS)
                .build()

            return GminiClient(
                baseUrl = url,
                rest = GminiRestClient(base, longLived, url, tokenProvider),
                ws = GminiWebSocketClient(socket, url, tokenProvider),
            )
        }

        public fun fromProfile(
            profile: ServerProfile,
            token: String?,
            base: OkHttpClient = baseHttpClient(),
        ): GminiClient = create(profile.httpBaseUrl, { token }, base)
    }
}
