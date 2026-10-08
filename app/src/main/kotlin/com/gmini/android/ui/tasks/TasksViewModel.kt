package com.gmini.android.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmini.android.core.model.Schedule
import com.gmini.android.core.model.TaskInfo
import com.gmini.android.core.model.TaskRequest
import com.gmini.android.core.net.ApiException
import com.gmini.android.data.GminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduleMode { ONCE, INTERVAL, CRON }

data class TasksUiState(
    val loading: Boolean = false,
    val tasks: List<TaskInfo> = emptyList(),
    val error: String? = null,
    val creating: Boolean = false,
    val createError: String? = null,
)

class TasksViewModel(private val repository: GminiRepository) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.listTasks() }
                .onSuccess { tasks -> _state.update { it.copy(loading = false, tasks = tasks) } }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = messageFor(throwable)) }
                }
        }
    }

    fun createTask(
        prompt: String,
        title: String,
        mode: ScheduleMode,
        intervalSeconds: String,
        cron: String,
        timezone: String,
        notify: String,
        onDone: () -> Unit,
    ) {
        if (prompt.isBlank()) {
            _state.update { it.copy(createError = "Escribe que debe hacer la tarea.") }
            return
        }
        val schedule = when (mode) {
            ScheduleMode.ONCE -> null
            ScheduleMode.INTERVAL -> {
                val seconds = intervalSeconds.toLongOrNull()
                if (seconds == null || seconds < 60) {
                    _state.update { it.copy(createError = "El intervalo minimo es 60 segundos.") }
                    return
                }
                Schedule(intervalSeconds = seconds)
            }

            ScheduleMode.CRON -> {
                if (cron.isBlank()) {
                    _state.update { it.copy(createError = "Escribe la expresion cron.") }
                    return
                }
                Schedule(cron = cron.trim(), timezone = timezone.ifBlank { "America/Lima" })
            }
        }
        val targets = notify.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        viewModelScope.launch {
            _state.update { it.copy(creating = true, createError = null) }
            runCatching {
                repository.createTask(
                    TaskRequest(
                        prompt = prompt.trim(),
                        title = title.trim().ifBlank { null },
                        schedule = schedule,
                        notify = targets,
                    ),
                )
            }
                .onSuccess {
                    _state.update { it.copy(creating = false) }
                    onDone()
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update { it.copy(creating = false, createError = messageFor(throwable)) }
                }
        }
    }

    fun cancelTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelTask(taskId) }
                .onSuccess { refresh() }
                .onFailure { throwable -> _state.update { it.copy(error = messageFor(throwable)) } }
        }
    }

    private fun messageFor(throwable: Throwable): String = when (throwable) {
        is ApiException -> throwable.message
        else -> "No se pudo completar la operacion de tareas."
    }
}
