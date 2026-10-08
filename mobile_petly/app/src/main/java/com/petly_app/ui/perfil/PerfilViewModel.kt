package com.petly_app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
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
    val message: String? = null
)

class PerfilViewModel(private val repository: PerfilRepository, private val preview: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(PerfilUiState())
    val state = _state.asStateFlow()
    init { load() }

    fun load() {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val form = repository.load() ?: PerfilDraft()
                _state.value = PerfilUiState(form = form)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = apiErrorMessage(e)) }
            }
        }
    }

    fun edit(form: PerfilDraft) {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(form = form.copy(finalizado = false), dirty = true, message = null,
            error = null, errors = emptyMap()) }
    }

    fun save(finalizar: Boolean) {
        if (_state.value.loading || _state.value.saving) return
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
                    message = if (preview) "Guardado temporal de prueba. Se pierde al cerrar el proceso o salir de la vista de prueba."
                    else if (finalizar) "Cuestionario guardado." else "Borrador guardado.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = apiErrorMessage(e)) }
            }
        }
    }
}
