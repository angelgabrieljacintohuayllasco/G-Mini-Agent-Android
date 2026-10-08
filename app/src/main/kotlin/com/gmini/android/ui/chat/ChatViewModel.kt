package com.gmini.android.ui.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmini.android.core.chat.ApprovalPrompt
import com.gmini.android.core.chat.ChatTurnReducer
import com.gmini.android.core.chat.ChatTurnState
import com.gmini.android.core.model.AgentStatus
import com.gmini.android.core.net.ApiException
import com.gmini.android.core.protocol.ChatEvent
import com.gmini.android.core.protocol.WsServerFrame
import com.gmini.android.core.protocol.toChatEvent
import com.gmini.android.data.AgentConnection
import com.gmini.android.data.ConnectionState
import com.gmini.android.data.GminiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val approval: ApprovalPrompt? = null,
    val agentStatus: AgentStatus = AgentStatus.IDLE,
    val connection: ConnectionState = ConnectionState(),
    val sessionId: String? = null,
    val transport: String? = null,
    val error: String? = null,
    val voiceState: VoiceState = VoiceState.IDLE,
)

/**
 * Gobierna un turno de chat. Prefiere el WebSocket vivo del servicio; si no hay
 * conexion, cae al streaming SSE de `POST /api/v1/chat`. En ambos casos los
 * eventos pasan por el mismo reductor del nucleo.
 */
class ChatViewModel(
    private val repository: GminiRepository,
    private val connection: AgentConnection,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val idGenerator = AtomicLong(0)
    private var active: ActiveTurn? = null
    private var sseJob: Job? = null

    private class ActiveTurn(val wsId: String?, val agentMessageId: Long, var turn: ChatTurnState)

    init {
        viewModelScope.launch {
            connection.state.collect { connectionState ->
                _state.update {
                    it.copy(
                        connection = connectionState,
                        agentStatus = if (active == null) connectionState.agentStatus else it.agentStatus,
                    )
                }
            }
        }
        viewModelScope.launch {
            connection.frames.collect { frame -> onWebSocketFrame(frame) }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun insertCommand(command: String) =
        _state.update { it.copy(input = if (it.input.isBlank()) "$command " else it.input) }

    fun newConversation() {
        cancelStreaming()
        active = null
        _state.update {
            it.copy(messages = emptyList(), sessionId = null, approval = null, error = null, sending = false)
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return

        appendMessage(ChatMessage(nextId(), Author.USER, text))
        val agentId = nextId()
        appendMessage(ChatMessage(agentId, Author.AGENT, "", streaming = true))
        _state.update { it.copy(input = "", sending = true, error = null, approval = null) }

        val sessionId = _state.value.sessionId

        if (connection.state.value.isConnected) {
            // El turno se registra ANTES de enviar el frame para no perder los
            // primeros chunks: el colector de frames ya tiene a quien entregarlos.
            val wsId = UUID.randomUUID().toString().take(8)
            active = ActiveTurn(wsId, agentId, ChatTurnState(sessionId = sessionId))
            _state.update { it.copy(transport = TRANSPORT_WS) }
            if (!connection.sendChat(wsId, text, sessionId)) {
                startSseFallback(text, sessionId, agentId)
            }
        } else {
            startSseFallback(text, sessionId, agentId)
        }
    }

    private fun startSseFallback(text: String, sessionId: String?, agentMessageId: Long) {
        active = ActiveTurn(null, agentMessageId, ChatTurnState(sessionId = sessionId))
        _state.update { it.copy(transport = TRANSPORT_SSE) }
        startSse(text, sessionId)
    }

    fun approve(approve: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(approval = null) }
            runCatching { repository.approve(approve) }
                .onFailure { failure -> _state.update { it.copy(error = messageFor(failure)) } }
        }
    }

    fun stop() {
        cancelStreaming()
        if (connection.state.value.isConnected) connection.cancelTurn()
        active?.let { finalize(it, interrupted = true) }
        active = null
        _state.update { it.copy(sending = false) }
    }

    fun resume(sessionId: String) {
        if (_state.value.sessionId == sessionId && _state.value.messages.isNotEmpty()) return
        cancelStreaming()
        active = null
        _state.update { it.copy(sessionId = sessionId, messages = emptyList(), error = null, sending = false) }
        viewModelScope.launch {
            runCatching { repository.sessionMessages(sessionId) }
                .onSuccess { history ->
                    _state.update {
                        it.copy(
                            messages = history.map { message ->
                                ChatMessage(
                                    id = nextId(),
                                    author = if (message.role == "user") Author.USER else Author.AGENT,
                                    text = message.content,
                                )
                            },
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = messageFor(failure)) } }
        }
    }

    fun onVoiceStateChange(voiceState: VoiceState) = _state.update { it.copy(voiceState = voiceState) }

    fun sendVoice(wav: ByteArray, onAudio: (ByteArray) -> Unit) {
        if (wav.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, voiceState = VoiceState.PROCESSING, error = null) }
            runCatching { repository.voiceTurn(wav, _state.value.sessionId) }
                .onSuccess { response ->
                    if (response.transcript.isNotBlank()) {
                        appendMessage(ChatMessage(nextId(), Author.USER, response.transcript))
                    }
                    appendMessage(ChatMessage(nextId(), Author.AGENT, response.reply))
                    _state.update {
                        it.copy(
                            sessionId = response.sessionId ?: it.sessionId,
                            sending = false,
                            voiceState = VoiceState.IDLE,
                        )
                    }
                    response.audioBase64
                        ?.let { encoded -> runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() }
                        ?.let(onAudio)
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(sending = false, voiceState = VoiceState.IDLE, error = messageFor(failure))
                    }
                }
        }
    }

    // --- Streaming interno -------------------------------------------------

    private fun startSse(text: String, sessionId: String?) {
        sseJob = viewModelScope.launch {
            try {
                repository.chatStream(text, sessionId).collect { event -> applyEvent(event) }
                active?.let { finalize(it, interrupted = false) }
                active = null
                _state.update { it.copy(sending = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (throwable: Throwable) {
                active?.let { markError(it.agentMessageId, messageFor(throwable)) }
                active = null
                _state.update { it.copy(sending = false, error = messageFor(throwable)) }
            }
        }
    }

    private fun onWebSocketFrame(frame: WsServerFrame) {
        val current = active ?: return
        val wsId = current.wsId ?: return // turno por SSE: ignora el WebSocket
        if (!frameMatches(frame, wsId)) return
        frame.toChatEvent()?.let { applyEvent(it) }
        if (frame is WsServerFrame.Done || frame is WsServerFrame.ErrorFrame) {
            finalize(current, interrupted = false)
            active = null
            _state.update { it.copy(sending = false) }
        }
    }

    private fun applyEvent(event: ChatEvent) {
        val current = active ?: return
        current.turn = ChatTurnReducer.reduce(current.turn, event)
        updateAgentMessage(current.agentMessageId, current.turn)
        _state.update {
            it.copy(
                approval = current.turn.approval,
                agentStatus = current.turn.status,
                sessionId = current.turn.sessionId ?: it.sessionId,
            )
        }
    }

    private fun finalize(turnState: ActiveTurn, interrupted: Boolean) {
        val turn = turnState.turn
        val finalText = when {
            turn.error != null -> turn.displayText
            interrupted && turn.displayText.isEmpty() -> "(interrumpido)"
            else -> turn.displayText
        }
        updateMessage(turnState.agentMessageId) {
            it.copy(
                text = finalText,
                streaming = false,
                actions = turn.actions,
                error = turn.error?.message ?: turn.error?.code,
            )
        }
    }

    private fun frameMatches(frame: WsServerFrame, wsId: String): Boolean = when (frame) {
        is WsServerFrame.Chunk -> frame.id == null || frame.id == wsId
        is WsServerFrame.Action -> frame.id == null || frame.id == wsId
        is WsServerFrame.ActionResult -> frame.id == null || frame.id == wsId
        is WsServerFrame.Done -> frame.id == null || frame.id == wsId
        is WsServerFrame.State,
        is WsServerFrame.Notice,
        is WsServerFrame.Approval,
        is WsServerFrame.ErrorFrame,
        -> true
        else -> false
    }

    private fun cancelStreaming() {
        sseJob?.cancel()
        sseJob = null
    }

    // --- Lista de mensajes -------------------------------------------------

    private fun appendMessage(message: ChatMessage) =
        _state.update { it.copy(messages = it.messages + message) }

    private fun updateAgentMessage(id: Long, turn: ChatTurnState) =
        updateMessage(id) { it.copy(text = turn.displayText, actions = turn.actions, streaming = !turn.done) }

    private fun markError(id: Long, message: String) =
        updateMessage(id) { it.copy(streaming = false, error = message) }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) =
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }

    private fun nextId(): Long = idGenerator.incrementAndGet()

    private fun messageFor(throwable: Throwable): String = when (throwable) {
        is ApiException -> if (throwable.isBusy) {
            "El agente esta ocupado con otra conversacion. Reintenta en un momento."
        } else {
            throwable.message
        }

        else -> "No se pudo completar la peticion. Revisa la conexion."
    }

    override fun onCleared() {
        cancelStreaming()
        super.onCleared()
    }

    private companion object {
        const val TRANSPORT_WS = "WebSocket"
        const val TRANSPORT_SSE = "SSE"
    }
}
