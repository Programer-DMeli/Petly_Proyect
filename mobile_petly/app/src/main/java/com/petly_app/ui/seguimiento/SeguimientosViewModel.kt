package com.petly_app.ui.seguimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.SeguimientoDto
import com.petly_app.data.remote.SeguimientosApi
import com.petly_app.data.remote.SolicitarAsesoriaDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class SeguimientosState(
    val items: List<SeguimientoDto> = emptyList(),
    val selectedId: Long? = null,
    val problema: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val fieldError: String? = null,
    val success: String? = null,
    val preview: Boolean = false
)

class SeguimientosViewModel(private val api: SeguimientosApi, private val preview: Boolean = false) : ViewModel() {
    private val mutable = MutableStateFlow(SeguimientosState(preview = preview))
    val state = mutable.asStateFlow()

    init { load() }

    fun load() = execute {
        val items = mutableListOf<SeguimientoDto>()
        var page = 1
        do {
            val response = api.listar(page++)
            items.addAll(response.results)
        } while (response.next != null)
        mutable.update { it.copy(items = items.distinctBy { row -> row.id }) }
    }

    fun select(id: Long?) {
        if (mutable.value.busy) return
        if (id != null && mutable.value.items.none { it.id == id && it.estado != "CANCELADO" }) return
        mutable.update { it.copy(selectedId = id, problema = "", fieldError = null, error = null, success = null) }
    }

    fun edit(problema: String) {
        if (!mutable.value.busy) mutable.update { it.copy(problema = problema, fieldError = null, error = null) }
    }

    fun submit() {
        if (preview || mutable.value.busy) return
        val current = mutable.value
        val id = current.selectedId ?: return
        val problema = current.problema.trim()
        if (problema.isEmpty() || problema.length > 2000) {
            mutable.update { it.copy(fieldError = "Describe el problema de adaptación usando entre 1 y 2000 caracteres.") }
            return
        }
        execute {
            val saved = api.solicitarAsesoria(SolicitarAsesoriaDto(id, problema))
            mutable.update { state -> state.copy(
                items = state.items.map { row -> if (row.id == id) row.copy(
                    asesorias_veterinarias = (row.asesorias_veterinarias + saved).distinctBy { it.id }
                ) else row },
                selectedId = null, problema = "",
                success = "Solicitud de asesoría veterinaria gratuita enviada al albergue responsable."
            ) }
        }
    }

    private fun execute(block: suspend () -> Unit) {
        if (preview || mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, success = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                val message = when {
                    e is IOException -> "No se pudo contactar con el servidor. Revisa tu conexión y vuelve a intentar."
                    e is HttpException && e.code() in listOf(400, 404) -> "No se pudo solicitar la asesoría. Revisa el problema y actualiza tus seguimientos."
                    else -> apiErrorMessage(e)
                }
                mutable.update { it.copy(error = message) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
