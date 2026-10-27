package com.petly_app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.model.Mascota
import com.petly_app.data.repository.CatalogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetalleMascotaUiState(val loading: Boolean = false, val mascota: Mascota? = null, val error: String? = null, val consejos: com.petly_app.data.model.ConsejosIA? = null, val consultandoIA: Boolean = false)

class DetalleMascotaViewModel(private val repository: CatalogoRepository, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(DetalleMascotaUiState())
    val state = _state.asStateFlow()
    init { load() }
    private var adviceJob: kotlinx.coroutines.Job? = null
    fun consejos() {
        if (_state.value.consultandoIA || _state.value.mascota == null) return
        _state.value = _state.value.copy(consultandoIA = true)
        adviceJob = viewModelScope.launch {
            try { _state.value = _state.value.copy(consejos = repository.consejos(id), consultandoIA = false) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(consultandoIA = false, consejos = com.petly_app.data.model.ConsejosIA("NO_DISPONIBLE", "No se pudo consultar Gemini. Puedes reintentar; el cálculo sigue disponible."))
            }
        }
    }
    fun load() {
        if (_state.value.loading) return
        adviceJob?.cancel()
        _state.value = DetalleMascotaUiState(loading = true)
        viewModelScope.launch {
            try {
                val mascota = repository.detail(id)
                _state.value = DetalleMascotaUiState(mascota = mascota,
                    error = if (mascota == null) "Esta mascota no está disponible para consulta." else null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = DetalleMascotaUiState(error = apiErrorMessage(e))
            }
        }
    }
}
