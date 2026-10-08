package com.gmini.android.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmini.android.core.model.DEFAULT_PORT
import com.gmini.android.core.net.ApiException
import com.gmini.android.core.net.NetworkScope
import com.gmini.android.core.pairing.PairingTarget
import com.gmini.android.core.pairing.PairingUri
import com.gmini.android.core.pairing.PairingUriResult
import com.gmini.android.data.GminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val host: String = "",
    val port: String = DEFAULT_PORT.toString(),
    val code: String = "",
    val label: String = "",
    val secure: Boolean = false,
    val remoteAccepted: Boolean = false,
    val isPairing: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val isRemoteHost: Boolean get() = host.isNotBlank() && NetworkScope.isRemote(host.trim())
}

class PairingViewModel(private val repository: GminiRepository) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun onHostChange(value: String) = _state.update { it.copy(host = value, error = null) }

    fun onPortChange(value: String) =
        _state.update { it.copy(port = value.filter(Char::isDigit).take(5), error = null) }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun onLabelChange(value: String) = _state.update { it.copy(label = value) }

    fun onSecureChange(value: Boolean) = _state.update { it.copy(secure = value) }

    fun onRemoteAcceptedChange(value: Boolean) = _state.update { it.copy(remoteAccepted = value) }

    /** Rellena el formulario desde un QR o un enlace gmini://pair. */
    fun applyScannedLink(raw: String) {
        when (val result = PairingUri.parse(raw)) {
            is PairingUriResult.Ok -> _state.update {
                it.copy(
                    host = result.target.host,
                    port = result.target.port.toString(),
                    code = result.target.code,
                    error = null,
                    info = "Codigo leido. Revisa los datos y empareja.",
                )
            }

            is PairingUriResult.Invalid -> _state.update {
                it.copy(error = "QR no valido: ${result.reason}")
            }
        }
    }

    fun pair(onPaired: () -> Unit) {
        val current = _state.value
        val port = current.port.toIntOrNull()
        when {
            current.host.isBlank() -> {
                _state.update { it.copy(error = "Falta el host del servidor.") }
                return
            }

            port == null || port !in 1..65535 -> {
                _state.update { it.copy(error = "El puerto no es valido.") }
                return
            }

            current.code.length != 6 -> {
                _state.update { it.copy(error = "El codigo debe tener 6 digitos.") }
                return
            }

            current.isRemoteHost && !current.remoteAccepted -> {
                _state.update { it.copy(error = "Confirma que entiendes el riesgo de un servidor remoto.") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(isPairing = true, error = null, info = null) }
            val result = runCatching {
                repository.pair(
                    target = PairingTarget(current.host.trim(), port!!, current.code),
                    label = current.label.trim(),
                    secure = current.secure,
                )
            }
            result
                .onSuccess {
                    _state.update { it.copy(isPairing = false) }
                    onPaired()
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isPairing = false, error = messageFor(throwable)) }
                }
        }
    }

    private fun messageFor(throwable: Throwable): String = when (throwable) {
        is ApiException -> when {
            throwable.httpStatus == 404 || throwable.code == "not_found" ->
                "Codigo invalido o vencido. Genera uno nuevo en tu G-Mini."
            throwable.code == "rate_limited" ->
                "Demasiados intentos. Espera un momento y reintenta."
            else -> throwable.message
        }

        else -> "No se pudo conectar. Revisa el host, el puerto y la red."
    }
}
