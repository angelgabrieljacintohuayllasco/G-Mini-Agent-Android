package com.gmini.android.ui.chat

import com.gmini.android.core.chat.TurnAction

enum class Author { USER, AGENT }

/** Un mensaje tal como se pinta en la lista del chat. */
data class ChatMessage(
    val id: Long,
    val author: Author,
    val text: String,
    val actions: List<TurnAction> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
)

/** Estado de la grabacion de voz (push-to-talk). */
enum class VoiceState { IDLE, RECORDING, PROCESSING }
