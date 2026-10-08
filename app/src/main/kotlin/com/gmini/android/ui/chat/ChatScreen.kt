package com.gmini.android.ui.chat

import android.Manifest
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.gmini.android.core.chat.TurnAction
import com.gmini.android.core.model.AgentStatus
import com.gmini.android.ui.GminiViewModelFactory
import com.gmini.android.ui.components.ConnectionBadge
import com.gmini.android.ui.components.EmptyState
import com.gmini.android.voice.AudioPlayback
import com.gmini.android.voice.VoiceRecorder
import androidx.compose.material.icons.rounded.Forum

private val slashCommands = listOf("/nuevo", "/ayuda", "/plan", "/modo", "/detener")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    factory: GminiViewModelFactory,
    resumeSessionId: String?,
) {
    val viewModel: ChatViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val recorder = remember { VoiceRecorder() }
    val playback = remember { AudioPlayback(context) }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    androidx.compose.runtime.LaunchedEffect(resumeSessionId) {
        if (resumeSessionId != null) viewModel.resume(resumeSessionId)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            playback.stop()
        }
    }

    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.connection.agentName ?: "G-Mini")
                        AgentStatusLine(state.agentStatus, state.transport)
                    }
                },
                actions = {
                    ConnectionBadge(state.connection, modifier = Modifier.padding(end = 8.dp))
                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Rounded.Add, contentDescription = "Nueva conversacion")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    EmptyState(
                        title = "Habla con tu agente",
                        message = "Escribe una orden o manten pulsado el microfono. Tu G-Mini la " +
                            "ejecuta en tu PC o servidor y veras aqui su avance.",
                        icon = Icons.Rounded.Forum,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }

            state.approval?.let { prompt ->
                ApprovalCard(
                    summary = prompt.summary,
                    onApprove = { viewModel.approve(true) },
                    onReject = { viewModel.approve(false) },
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SlashCommandRow(
                onCommand = { command ->
                    if (command == "/nuevo") viewModel.newConversation() else viewModel.insertCommand(command)
                },
            )

            Composer(
                input = state.input,
                sending = state.sending,
                recording = state.voiceState == VoiceState.RECORDING,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onMicPress = {
                    if (micPermission.status.isGranted) {
                        recorder.start()
                        viewModel.onVoiceStateChange(VoiceState.RECORDING)
                        true
                    } else {
                        micPermission.launchPermissionRequest()
                        false
                    }
                },
                onMicRelease = {
                    if (state.voiceState == VoiceState.RECORDING) {
                        val wav = recorder.stop()
                        viewModel.sendVoice(wav) { audio -> playback.play(audio) }
                    }
                },
            )
        }
    }
}

@Composable
private fun AgentStatusLine(status: AgentStatus, transport: String?) {
    val label = when (status) {
        AgentStatus.LISTENING -> "Escuchando"
        AgentStatus.THINKING -> "Pensando"
        AgentStatus.ACTING -> "Ejecutando acciones"
        AgentStatus.SPEAKING -> "Hablando"
        else -> transport?.let { "Listo - $it" } ?: "Listo"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.author == Author.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val container = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = container,
            contentColor = onContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.actions.isNotEmpty()) {
                    message.actions.forEach { ActionRow(it) }
                    if (message.text.isNotEmpty()) Spacer(Modifier.size(6.dp))
                }
                when {
                    message.text.isNotEmpty() -> Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    message.streaming -> Text("...", style = MaterialTheme.typography.bodyLarge)
                }
                message.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: TurnAction) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            action.running -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            action.success == false -> Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            else -> Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = action.message ?: action.action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApprovalCard(summary: String?, onApprove: () -> Unit, onReject: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Aprobacion requerida", style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary ?: "El agente quiere ejecutar una accion sensible.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Rechazar") }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Aprobar") }
            }
        }
    }
}

@Composable
private fun SlashCommandRow(onCommand: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slashCommands.forEach { command ->
            AssistChip(onClick = { onCommand(command) }, label = { Text(command) })
        }
    }
}

@Composable
private fun Composer(
    input: String,
    sending: Boolean,
    recording: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicPress: () -> Boolean,
    onMicRelease: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Grabando..." else "Escribe una orden") },
                maxLines = 4,
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val started = onMicPress()
                                if (started) {
                                    tryAwaitRelease()
                                    onMicRelease()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Mantener para hablar",
                    tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            if (sending) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Detener")
                }
            } else {
                FilledIconButton(onClick = onSend, enabled = input.isNotBlank()) {
                    Icon(Icons.Rounded.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}
