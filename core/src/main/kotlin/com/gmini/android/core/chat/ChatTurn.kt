package com.gmini.android.core.chat

import com.gmini.android.core.model.AgentEmotion
import com.gmini.android.core.model.AgentStatus
import com.gmini.android.core.protocol.ChatEvent

/** Una accion que el agente ejecuta (o ya ejecuto) durante el turno. */
public data class TurnAction(
    val id: String?,
    val action: String,
    val running: Boolean,
    val success: Boolean? = null,
    val message: String? = null,
)

/** Aprobacion pendiente que el usuario debe confirmar o rechazar. */
public data class ApprovalPrompt(val summary: String?)

/** Error que corta un turno. */
public data class TurnError(val code: String, val message: String?)

/**
 * Estado acumulado de un turno de chat en streaming. Es inmutable: cada
 * [ChatEvent] produce un estado nuevo mediante [ChatTurnReducer]. Vive en el
 * nucleo para poder probar la logica mas delicada del chat en la JVM, sin
 * depender de Android.
 */
public data class ChatTurnState(
    val sessionId: String? = null,
    val streamingText: String = "",
    val actions: List<TurnAction> = emptyList(),
    val status: AgentStatus = AgentStatus.IDLE,
    val emotion: AgentEmotion = AgentEmotion.NEUTRAL,
    val notices: List<String> = emptyList(),
    val approval: ApprovalPrompt? = null,
    val done: Boolean = false,
    val reply: String? = null,
    val error: TurnError? = null,
) {
    /** Texto que debe mostrarse: la respuesta final si ya llego, o lo acumulado. */
    val displayText: String get() = reply?.takeIf { it.isNotEmpty() } ?: streamingText
}

public object ChatTurnReducer {

    public fun reduce(state: ChatTurnState, event: ChatEvent): ChatTurnState = when (event) {
        is ChatEvent.Start -> state.copy(sessionId = event.sessionId ?: state.sessionId)

        is ChatEvent.Chunk -> state.copy(streamingText = state.streamingText + event.text)

        is ChatEvent.ActionStarted -> state.copy(
            actions = state.actions + TurnAction(
                id = event.id,
                action = event.action,
                running = true,
            ),
        )

        is ChatEvent.ActionFinished -> state.copy(actions = state.finishAction(event))

        is ChatEvent.StateChanged -> state.copy(
            status = AgentStatus.from(event.status),
            emotion = AgentEmotion.from(event.emotion),
        )

        is ChatEvent.Notice -> state.copy(notices = state.notices + event.text)

        is ChatEvent.ApprovalRequested ->
            state.copy(approval = if (event.pending) ApprovalPrompt(event.summary) else null)

        is ChatEvent.Done -> state.copy(
            done = true,
            reply = event.reply ?: state.streamingText,
            sessionId = event.sessionId ?: state.sessionId,
            status = AgentStatus.IDLE,
        )

        is ChatEvent.Failed -> state.copy(
            done = true,
            error = TurnError(event.code, event.message),
            status = AgentStatus.IDLE,
        )
    }

    public fun reduce(initial: ChatTurnState, events: Iterable<ChatEvent>): ChatTurnState =
        events.fold(initial, ::reduce)

    /**
     * Cierra la accion que corresponde al evento: por `id` si lo trae, o la
     * ultima en curso con el mismo nombre.
     */
    private fun ChatTurnState.finishAction(event: ChatEvent.ActionFinished): List<TurnAction> {
        val index = when {
            event.id != null -> actions.indexOfLast { it.id == event.id }
            else -> actions.indexOfLast { it.action == event.action && it.running }
        }
        if (index < 0) {
            return actions + TurnAction(
                id = event.id,
                action = event.action,
                running = false,
                success = event.success,
                message = event.message,
            )
        }
        return actions.toMutableList().also {
            it[index] = it[index].copy(
                running = false,
                success = event.success,
                message = event.message,
            )
        }
    }
}
