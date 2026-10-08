package com.gmini.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val PAIRING = "pairing"
    const val CHAT = "chat"
    const val CHAT_WITH_SESSION = "chat?session={sessionId}"
    const val SESSIONS = "sessions"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"

    fun chatForSession(sessionId: String): String = "chat?session=$sessionId"
}

/** Destino de la barra inferior. */
data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topDestinations: List<TopDestination> = listOf(
    TopDestination(Routes.CHAT, "Chat", Icons.Rounded.Forum),
    TopDestination(Routes.SESSIONS, "Sesiones", Icons.Rounded.History),
    TopDestination(Routes.TASKS, "Tareas", Icons.Rounded.Schedule),
    TopDestination(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)
