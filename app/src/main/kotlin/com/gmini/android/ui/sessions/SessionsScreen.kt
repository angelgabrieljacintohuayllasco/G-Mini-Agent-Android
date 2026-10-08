package com.gmini.android.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmini.android.core.model.SessionSummary
import com.gmini.android.ui.GminiViewModelFactory
import com.gmini.android.ui.components.EmptyState
import com.gmini.android.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    factory: GminiViewModelFactory,
    onOpenSession: (String) -> Unit,
) {
    val viewModel: SessionsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sesiones") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.sessions.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }
                }

                state.error != null && state.sessions.isEmpty() -> {
                    EmptyState(
                        title = "No se pudieron cargar",
                        message = state.error ?: "",
                        icon = Icons.Rounded.History,
                    )
                }

                state.sessions.isEmpty() -> {
                    EmptyState(
                        title = "Sin conversaciones",
                        message = "Cuando hables con tu agente, las conversaciones apareceran aqui " +
                            "para retomarlas.",
                        icon = Icons.Rounded.History,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(session) { onOpenSession(session.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            text = session.title?.takeIf { it.isNotBlank() } ?: "Conversacion",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = buildString {
                append("${session.messageCount} mensajes")
                session.updatedAt?.let { append("  -  ${it.take(16).replace('T', ' ')}") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
