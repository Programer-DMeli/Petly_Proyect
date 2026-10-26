package com.petly_app.ui.perfil

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.data.model.KycConsultaDto
import com.petly_app.data.model.KycDto
import com.petly_app.data.repository.KycRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KycUiState(
    val dni: String = "", val nombres: String = "", val apellidos: String = "",
    val anverso: Uri? = null, val reverso: Uri? = null,
    val record: KycDto? = null, val resultado: KycConsultaDto? = null,
    val loading: Boolean = false, val saving: Boolean = false, val error: String? = null, val message: String? = null
)

class KycViewModel(private val repository: KycRepository) : ViewModel() {
    private val _state = MutableStateFlow(KycUiState(loading = true))
    val state = _state.asStateFlow()

    init { load() }

    fun edit(dni: String? = null, nombres: String? = null, apellidos: String? = null) {
        _state.value = _state.value.copy(dni = dni ?: _state.value.dni, nombres = nombres ?: _state.value.nombres,
            apellidos = apellidos ?: _state.value.apellidos, error = null, message = null)
    }
    fun selectFront(uri: Uri?) { _state.value = _state.value.copy(anverso = uri, error = null) }
    fun selectBack(uri: Uri?) { _state.value = _state.value.copy(reverso = uri, error = null) }

    fun load() = runRequest { current ->
        val record = repository.load()
        current.copy(record = record, dni = record?.dni ?: current.dni, nombres = record?.nombres ?: current.nombres,
            apellidos = record?.apellidos ?: current.apellidos, loading = false)
    }

    fun upload() {
        val s = _state.value
        if (!s.dni.matches(Regex("[0-9]{8}"))) { _state.value = s.copy(error = "El DNI debe tener exactamente ocho dígitos."); return }
        if (s.nombres.isBlank() || s.apellidos.isBlank()) { _state.value = s.copy(error = "Completa nombres y apellidos tal como aparecen en el DNI."); return }
        if (s.anverso == null || s.reverso == null) { _state.value = s.copy(error = "Selecciona ambas caras del DNI."); return }
        _state.value = s.copy(saving = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val saved = repository.upload(s.dni, s.nombres, s.apellidos, s.anverso, s.reverso)
                _state.value = _state.value.copy(record = saved, saving = false, message = "Documentos enviados de forma privada.")
            } catch (e: Exception) { if (e is CancellationException) throw e; _state.value = _state.value.copy(saving = false, error = e.message ?: "No se pudieron enviar los documentos.") }
        }
    }

    fun consultar() {
        val record = _state.value.record ?: run { _state.value = _state.value.copy(error = "Primero carga los documentos del DNI."); return }
        _state.value = _state.value.copy(saving = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val result = repository.consultar(record.id)
                _state.value = _state.value.copy(resultado = result, saving = false, message = if (result.estado == "VERIFICADO") "Los datos coinciden con la consulta disponible." else "El caso permanece pendiente de revisión.")
            } catch (e: Exception) { if (e is CancellationException) throw e; _state.value = _state.value.copy(saving = false, error = e.message ?: "No se pudo consultar el servicio.") }
        }
    }

    private fun runRequest(transform: (KycUiState) -> KycUiState) {
        viewModelScope.launch {
            try { _state.value = transform(_state.value) }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.value = _state.value.copy(loading = false, error = e.message ?: "No se pudo cargar la verificación.") }
        }
    }
}
