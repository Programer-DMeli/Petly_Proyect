package com.petly_app.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.repository.AuthRepository
import com.petly_app.data.repository.HealthRepository
import kotlinx.coroutines.CancellationException
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
    val registered: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val healthRepository: HealthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

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
        _state.update { it.copy(error = null, registered = false) }
    }

    private fun validate(email: String, password: String): String? = when {
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Ingresa un correo válido."
        password.isBlank() -> "Ingresa tu contraseña."
        else -> null
    }

    fun login(email: String, password: String) {
        if (_state.value.busy) return
        val error = validate(email, password)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        execute { authRepository.login(email, password) }
    }

    fun register(email: String, password: String, confirmation: String) {
        if (_state.value.busy) return
        // Política propuesta de registro. Acordar el mínimo con Persona B.
        val error = validate(email, password) ?: when {
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        execute {
            authRepository.register(email, password)
            _state.update { it.copy(registered = true) }
        }
    }

    private fun execute(operation: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, registered = false) }
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = apiErrorMessage(error)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
