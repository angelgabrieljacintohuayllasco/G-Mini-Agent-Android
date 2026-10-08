package com.gmini.android.data

import android.os.Build
import com.gmini.android.BuildConfig
import com.gmini.android.core.model.AgentEmotion
import com.gmini.android.core.model.AgentStatus
import com.gmini.android.core.net.GminiClient
import com.gmini.android.core.net.WS_CLOSE_UNAUTHORIZED
import com.gmini.android.core.net.WsConnection
import com.gmini.android.core.net.WsEvent
import com.gmini.android.core.protocol.WsClientFrame
import com.gmini.android.core.protocol.WsServerFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, UNAUTHORIZED, ERROR }

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val agentName: String? = null,
    val agentStatus: AgentStatus = AgentStatus.IDLE,
    val emotion: AgentEmotion = AgentEmotion.NEUTRAL,
    val message: String? = null,
) {
    val isConnected: Boolean get() = status == ConnectionStatus.CONNECTED
}

/**
 * Mantiene viva una unica conexion WebSocket con el servidor activo y reparte
 * los frames entrantes a quien los escuche (el servicio en primer plano para
 * las notificaciones y los nodos; el chat para el streaming). Reconecta con
 * espera exponencial, salvo que el servidor rechace el token.
 */
class AgentConnection(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<WsServerFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<WsServerFrame> = _frames.asSharedFlow()

    @Volatile
    private var connection: WsConnection? = null
    private var loopJob: Job? = null

    @Volatile
    private var deviceName: String = Build.MODEL ?: "Android"

    @Volatile
    private var surfaces: List<String> = emptyList()

    fun start(client: GminiClient, deviceName: String, surfaces: List<String>) {
        this.deviceName = deviceName
        this.surfaces = surfaces
        loopJob?.cancel()
        _state.value = ConnectionState(status = ConnectionStatus.CONNECTING)
        loopJob = scope.launch { runLoop(client) }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        connection?.close(1000, "cliente cerrado")
        connection = null
        _state.value = ConnectionState(status = ConnectionStatus.DISCONNECTED)
    }

    fun send(frame: WsClientFrame): Boolean = connection?.send(frame) ?: false

    fun sendChat(id: String, text: String, sessionId: String?): Boolean =
        send(WsClientFrame.Chat(id = id, text = text, sessionId = sessionId))

    fun cancelTurn(): Boolean = send(WsClientFrame.Cancel)

    private suspend fun runLoop(client: GminiClient) {
        var backoffMs = 1_000L
        while (currentCoroutineContext().isActive) {
            _state.update { it.copy(status = ConnectionStatus.CONNECTING, message = null) }
            try {
                client.ws.open().collect { event -> onEvent(event) { backoffMs = 1_000L } }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                _state.update { it.copy(status = ConnectionStatus.ERROR, message = t.message) }
            }
            connection = null

            if (_state.value.status == ConnectionStatus.UNAUTHORIZED) break
            if (!currentCoroutineContext().isActive) break

            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private fun onEvent(event: WsEvent, onConnected: () -> Unit) {
        when (event) {
            is WsEvent.Open -> {
                connection = event.connection
                onConnected()
                event.connection.send(
                    WsClientFrame.Hello(
                        client = "android",
                        version = BuildConfig.VERSION_NAME,
                        deviceName = deviceName,
                    ),
                )
                if (surfaces.isNotEmpty()) {
                    event.connection.send(
                        WsClientFrame.NodeRegister(
                            surfaces = surfaces,
                            platform = "android",
                            meta = mapOf(
                                "model" to (Build.MODEL ?: "Android"),
                                "app" to BuildConfig.VERSION_NAME,
                            ),
                        ),
                    )
                }
                _state.update { it.copy(status = ConnectionStatus.CONNECTED, message = null) }
            }

            is WsEvent.Incoming -> {
                applyFrame(event.frame)
                _frames.tryEmit(event.frame)
            }

            is WsEvent.Closed -> {
                if (event.code == WS_CLOSE_UNAUTHORIZED) {
                    _state.update {
                        it.copy(
                            status = ConnectionStatus.UNAUTHORIZED,
                            message = "El servidor rechazo el token. Vuelve a emparejar.",
                        )
                    }
                }
            }

            is WsEvent.Failed -> {
                val status = if (event.httpStatus == 401) {
                    ConnectionStatus.UNAUTHORIZED
                } else {
                    ConnectionStatus.ERROR
                }
                _state.update { it.copy(status = status, message = event.error?.message) }
            }
        }
    }

    private fun applyFrame(frame: WsServerFrame) {
        when (frame) {
            is WsServerFrame.Ready -> _state.update {
                it.copy(agentName = frame.agentName ?: it.agentName)
            }

            is WsServerFrame.State -> _state.update {
                it.copy(
                    agentStatus = AgentStatus.from(frame.status),
                    emotion = AgentEmotion.from(frame.emotion),
                )
            }

            else -> Unit
        }
    }
}
