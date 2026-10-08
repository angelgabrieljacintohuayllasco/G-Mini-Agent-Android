package com.gmini.android.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmini.android.core.model.SessionSummary
import com.gmini.android.core.net.ApiException
import com.gmini.android.data.GminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val loading: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val error: String? = null,
)

class SessionsViewModel(private val repository: GminiRepository) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.listSessions(limit = 50) }
                .onSuccess { sessions -> _state.update { it.copy(loading = false, sessions = sessions) } }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = messageFor(throwable)) }
                }
        }
    }

    private fun messageFor(throwable: Throwable): String = when (throwable) {
        is ApiException -> throwable.message
        else -> "No se pudieron cargar las sesiones."
    }
}
