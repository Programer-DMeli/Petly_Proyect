package com.petly_app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.model.DisponibilidadValidation
import com.petly_app.data.model.FranjaHoraria
import com.petly_app.data.repository.DisponibilidadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisponibilidadUiState(
    val franjas: List<FranjaHoraria> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val validationError: String? = null,
    val error: String? = null,
    val message: String? = null,
    val loaded: Boolean = false
)

class DisponibilidadViewModel(
    private val repository: DisponibilidadRepository,
    private val preview: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(DisponibilidadUiState())
    val state: StateFlow<DisponibilidadUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val list = repository.list()
                _state.value = DisponibilidadUiState(franjas = list, loaded = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = apiErrorMessage(e)) }
            }
        }
    }

    fun addFranja(diaSemana: String, horaInicio: String, horaFin: String, descripcion: String) {
        val nextId = (_state.value.franjas.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
        val newFranja = FranjaHoraria(nextId, diaSemana.trim(), horaInicio.trim(), horaFin.trim(), descripcion.trim())

        val validationError = DisponibilidadValidation.validateNoOverlap(_state.value.franjas, newFranja)
        if (validationError != null) {
            _state.update { it.copy(validationError = validationError, message = null) }
            return
        }

        val updated = _state.value.franjas + newFranja
        _state.update {
            it.copy(
                franjas = updated,
                validationError = null,
                message = "Franja horaria agregada. Recuerda pulsar Guardar."
            )
        }
    }

    fun removeFranja(id: Long) {
        val updated = _state.value.franjas.filter { it.id != id }
        _state.update {
            it.copy(
                franjas = updated,
                validationError = null,
                message = "Franja eliminada de la lista."
            )
        }
    }

    fun clearValidationError() {
        _state.update { it.copy(validationError = null) }
    }

    fun save() {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(saving = true, error = null, validationError = null, message = null) }
        viewModelScope.launch {
            try {
                val result = repository.save(_state.value.franjas)
                _state.update {
                    it.copy(
                        franjas = result,
                        saving = false,
                        message = if (preview) "Horarios guardados temporalmente en modo prueba (America/Lima)."
                        else "Horarios de disponibilidad actualizados correctamente (America/Lima)."
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = apiErrorMessage(e)) }
            }
        }
    }
}
