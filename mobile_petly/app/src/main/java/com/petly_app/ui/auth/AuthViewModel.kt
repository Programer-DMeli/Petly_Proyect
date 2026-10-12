package com.petly_app.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.repository.AuthRepository
import com.petly_app.data.repository.HealthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val busy: Boolean = false,
    val checkingConnection: Boolean = false,
    val connected: Boolean = false,
    val connectionMessage: String? = null,
    val error: String? = null,
    val registered: Boolean = false,
    val blockedEmail: String? = null,
    val lockRemainingSeconds: Int = 0,
    val rateLimitRemainingSeconds: Int = 0,
    val errorStatus: Int? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val healthRepository: HealthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    private var lockUntil = 0L
    private var rateUntil = 0L
    private var cooldownJob: Job? = null
    private fun monotonicMillis() = System.nanoTime() / 1_000_000
    private fun secondsUntil(deadline: Long) = if (deadline == 0L) 0 else ((deadline - monotonicMillis() + 999) / 1000).coerceAtLeast(0).toInt()

    private fun applyCooldown(error: HttpException, email: String?) {
        val status = error.code()
        if (status != 423 && status != 429) return
        val seconds = (error.response()?.headers()?.get("Retry-After")?.toIntOrNull()
            ?: 60).coerceIn(1, 86400)
        if (status == 423 && email != null) {
            lockUntil = monotonicMillis() + seconds * 1000L
            _state.update { it.copy(blockedEmail = email.trim().lowercase(), lockRemainingSeconds = seconds) }
        } else if (status == 429) {
            rateUntil = monotonicMillis() + seconds * 1000L
            _state.update { it.copy(rateLimitRemainingSeconds = seconds) }
        }
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            do {
                val lock = secondsUntil(lockUntil)
                val rate = secondsUntil(rateUntil)
                _state.update {
                    val expired = (it.errorStatus == 423 && lock == 0) || (it.errorStatus == 429 && rate == 0)
                    it.copy(lockRemainingSeconds = lock, rateLimitRemainingSeconds = rate,
                        blockedEmail = if (lock == 0) null else it.blockedEmail,
                        error = if (expired) null else it.error,
                        errorStatus = if (expired) null else it.errorStatus)
                }
                if (lock == 0 && rate == 0) break
                delay(1000)
            } while (true)
        }
    }

    init { checkConnection() }

    fun checkConnection() {
        if (_state.value.checkingConnection) return
        _state.update { it.copy(checkingConnection = true, connectionMessage = null) }
        viewModelScope.launch {
            try {
                healthRepository.check()
                _state.update { it.copy(connected = true, connectionMessage = "Spring Boot conectado.") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(connected = false, connectionMessage = apiErrorMessage(error)) }
            } finally {
                _state.update { it.copy(checkingConnection = false) }
            }
        }
    }

    fun clearFormState() {
        _state.update { it.copy(error = null, errorStatus = null, registered = false) }
    }

    private fun validate(email: String, password: String): String? = when {
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Ingresa un correo válido, por ejemplo nombre@dominio.com. Este error de formato no cuenta como intento de acceso."
        password.isBlank() -> "Ingresa tu contraseña."
        else -> null
    }

    fun login(email: String, password: String) {
        if (_state.value.busy || _state.value.rateLimitRemainingSeconds > 0) return
        if (_state.value.blockedEmail == email.trim().lowercase() && _state.value.lockRemainingSeconds > 0) return
        val error = validate(email, password)
        if (error != null) {
            _state.update { it.copy(error = error, errorStatus = null) }
            return
        }
        execute(email) { authRepository.login(email, password) }
    }

    fun register(email: String, password: String, confirmation: String) {
        if (_state.value.busy || _state.value.rateLimitRemainingSeconds > 0) return
        // Política propuesta de registro. Acordar el mínimo con Persona B.
        val error = validate(email, password) ?: when {
            (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72) -> "Usa al menos 8 caracteres y hasta 72 bytes UTF-8."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error, errorStatus = null) }
            return
        }
        execute {
            authRepository.register(email, password)
            _state.update { it.copy(registered = true) }
        }
    }

    private fun execute(email: String? = null, operation: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, errorStatus = null, registered = false) }
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = apiErrorMessage(error), errorStatus = (error as? HttpException)?.code()) }
                if (error is HttpException) applyCooldown(error, email)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
