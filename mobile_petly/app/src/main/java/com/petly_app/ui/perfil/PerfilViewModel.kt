package com.petly_app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiFieldErrors
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.model.UserDto
import com.petly_app.data.model.PerfilDraft
import com.petly_app.data.model.PerfilValidation
import com.petly_app.data.repository.PerfilRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerfilUiState(
    val form: PerfilDraft = PerfilDraft(), val loading: Boolean = false,
    val saving: Boolean = false, val dirty: Boolean = false,
    val errors: Map<String, String> = emptyMap(), val error: String? = null,
    val message: String? = null, val loaded: Boolean = false,
    val saveRevision: Long = 0,
    val user: UserDto? = null,
    val applications: Int? = null,
    val verified: Boolean? = null,
    val summaryError: String? = null
)

class PerfilViewModel(
    private val repository: PerfilRepository,
    private val preview: Boolean,
    private val loadUser: (suspend () -> UserDto)? = null,
    private val loadApplications: (suspend () -> Int)? = null,
    private val loadVerified: (suspend () -> Boolean)? = null
) : ViewModel() {
    private val _state = MutableStateFlow(PerfilUiState())
    val state = _state.asStateFlow()
    init { load() }

    fun load() {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val form = repository.load() ?: PerfilDraft()
                _state.update { it.copy(form = form, loaded = true, loading = false, dirty = false, errors = emptyMap(), message = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = apiErrorMessage(e)) }
            }
        }
    }

    fun refreshSummary() {
        if (preview) return
        viewModelScope.launch {
            _state.update { it.copy(summaryError = null) }
            suspend fun <T> fetch(loader: (suspend () -> T)?, apply: (T) -> Unit) {
                try {
                    loader?.invoke()?.let(apply)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _state.update { it.copy(summaryError = apiErrorMessage(e)) }
                }
            }
            fetch(loadUser) { value -> _state.update { it.copy(user = value) } }
            fetch(loadApplications) { value -> _state.update { it.copy(applications = value) } }
            fetch(loadVerified) { value -> _state.update { it.copy(verified = value) } }
        }
    }

    fun edit(form: PerfilDraft) {
        if (_state.value.loading || _state.value.saving || !_state.value.loaded) return
        _state.update { it.copy(form = form.copy(finalizado = false), dirty = true, message = null,
            error = null, errors = emptyMap()) }
    }

    fun save(finalizar: Boolean) {
        if (_state.value.loading || _state.value.saving || !_state.value.loaded) return
        val form = _state.value.form.copy(finalizado = finalizar)
        val errors = PerfilValidation.validate(form, finalizar)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, message = null) }
            return
        }
        _state.update { it.copy(saving = true, error = null, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            try {
                repository.save(form)
                _state.update { it.copy(form = form, saving = false, dirty = false,
                    saveRevision = it.saveRevision + 1,
                    message = if (preview) "Guardado temporal de prueba. Se pierde al cerrar el proceso o salir de la vista de prueba."
                    else if (finalizar) "Cuestionario guardado." else "Borrador guardado.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = apiErrorMessage(e), errors = apiFieldErrors(e)) }
            }
        }
    }
}
