package com.gmini.android.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmini.android.core.model.TaskInfo
import com.gmini.android.ui.GminiViewModelFactory
import com.gmini.android.ui.components.EmptyState
import com.gmini.android.ui.components.SectionCard
import com.gmini.android.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(factory: GminiViewModelFactory) {
    val viewModel: TasksViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tareas") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Nueva tarea")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.tasks.isEmpty()) {
                EmptyState(
                    title = "Sin tareas",
                    message = "Crea tareas para que tu agente trabaje 24/7: una sola vez, cada " +
                        "cierto tiempo o con una expresion cron.",
                    icon = Icons.Rounded.Add,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.tasks, key = { it.taskId }) { task ->
                        TaskRow(task, onCancel = { viewModel.cancelTask(task.taskId) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        NewTaskDialog(
            creating = state.creating,
            error = state.createError,
            onDismiss = { showCreate = false },
            onCreate = { prompt, title, mode, interval, cron, tz, notify ->
                viewModel.createTask(prompt, title, mode, interval, cron, tz, notify) {
                    showCreate = false
                }
            },
        )
    }
}

@Composable
private fun TaskRow(task: TaskInfo, onCancel: () -> Unit) {
    val (label, color) = statusAppearance(task.status)
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = task.title?.takeIf { it.isNotBlank() } ?: (task.prompt ?: "Tarea"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = label, color = color)
        }
        task.prompt?.takeIf { it.isNotBlank() && it != task.title }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        task.nextRunAt?.let {
            Text(
                text = "Proxima: ${it.take(16).replace('T', ' ')}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        task.result?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        if (task.status in activeStatuses) {
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 2.dp)) {
                Text("Cancelar tarea")
            }
        }
    }
}

@Composable
private fun statusAppearance(status: String): Pair<String, androidx.compose.ui.graphics.Color> = when (status) {
    "done" -> "Completada" to MaterialTheme.colorScheme.primary
    "failed" -> "Fallo" to MaterialTheme.colorScheme.error
    "running" -> "En curso" to MaterialTheme.colorScheme.tertiary
    "scheduled" -> "Programada" to MaterialTheme.colorScheme.secondary
    "cancelled" -> "Cancelada" to MaterialTheme.colorScheme.outline
    else -> "En cola" to MaterialTheme.colorScheme.secondary
}

private val activeStatuses = setOf("queued", "scheduled", "running")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskDialog(
    creating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String, ScheduleMode, String, String, String, String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ScheduleMode.ONCE) }
    var interval by remember { mutableStateOf("3600") }
    var cron by remember { mutableStateOf("0 8 * * *") }
    var timezone by remember { mutableStateOf("America/Lima") }
    var notify by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onCreate(prompt, title, mode, interval, cron, timezone, notify) },
                enabled = !creating,
            ) { Text(if (creating) "Creando..." else "Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Nueva tarea") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Que debe hacer") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == ScheduleMode.ONCE, { mode = ScheduleMode.ONCE }, label = { Text("Una vez") })
                    FilterChip(mode == ScheduleMode.INTERVAL, { mode = ScheduleMode.INTERVAL }, label = { Text("Intervalo") })
                    FilterChip(mode == ScheduleMode.CRON, { mode = ScheduleMode.CRON }, label = { Text("Cron") })
                }
                when (mode) {
                    ScheduleMode.INTERVAL -> OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter(Char::isDigit) },
                        label = { Text("Cada cuantos segundos (min 60)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ScheduleMode.CRON -> {
                        OutlinedTextField(
                            value = cron,
                            onValueChange = { cron = it },
                            label = { Text("Cron (min hora dia mes dia-sem)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = timezone,
                            onValueChange = { timezone = it },
                            label = { Text("Zona horaria") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    ScheduleMode.ONCE -> Unit
                }
                OutlinedTextField(
                    value = notify,
                    onValueChange = { notify = it },
                    label = { Text("Avisar a (ej. telegram), separar por comas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
