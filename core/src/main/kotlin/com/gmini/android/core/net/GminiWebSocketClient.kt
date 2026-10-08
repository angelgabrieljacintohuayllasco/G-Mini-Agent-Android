package com.gmini.android.core.net

import com.gmini.android.core.protocol.FrameCodec
import com.gmini.android.core.protocol.WsClientFrame
import com.gmini.android.core.protocol.WsServerFrame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Codigo de cierre que el servidor usa cuando el token no es valido. */
public const val WS_CLOSE_UNAUTHORIZED: Int = 4401

/** Conexion viva con el WebSocket, para enviar frames desde el lado del cliente. */
public interface WsConnection {
    public fun send(frame: WsClientFrame): Boolean
    public fun ping(): Boolean
    public fun close(code: Int = 1000, reason: String? = null)
}

/** Eventos del ciclo de vida del WebSocket que ve la capa de aplicacion. */
public sealed interface WsEvent {
    public data class Open(val connection: WsConnection) : WsEvent
    public data class Incoming(val frame: WsServerFrame) : WsEvent
    public data class Closed(val code: Int, val reason: String) : WsEvent
    public data class Failed(val error: Throwable?, val httpStatus: Int?) : WsEvent
}

/**
 * Abre el WebSocket `/api/v1/ws`. El token viaja en la cabecera `Authorization`
 * del handshake. OkHttp hace el upgrade a partir de la URL http(s), asi que no
 * hace falta construir una URL `ws://` a mano.
 */
public class GminiWebSocketClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val tokenProvider: () -> String?,
) {
    public fun open(): Flow<WsEvent> = callbackFlow {
        val url = baseUrl.newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("ws")
            .build()

        val request = Request.Builder().url(url).apply {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(WsEvent.Open(OkHttpWsConnection(webSocket)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching { FrameCodec.decode(text) }.getOrNull() ?: return
                trySend(WsEvent.Incoming(frame))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySend(WsEvent.Closed(code, reason))
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(WsEvent.Failed(t, response?.code))
                close()
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }

    private class OkHttpWsConnection(private val webSocket: WebSocket) : WsConnection {
        override fun send(frame: WsClientFrame): Boolean = webSocket.send(FrameCodec.encode(frame))
        override fun ping(): Boolean = webSocket.send(FrameCodec.encode(WsClientFrame.Ping))
        override fun close(code: Int, reason: String?) {
            webSocket.close(code, reason)
        }
    }
}
