package com.petly_app.ui.seguimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.AgendaVacunasApi
import com.petly_app.data.remote.AgendaVacunasDto
import com.petly_app.data.remote.DatosVacunaProgramada
import com.petly_app.data.remote.VacunaProgramadaDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale

internal const val MAX_NOMBRE_VACUNA = 120

data class VacunaEnEdicion(val id: Long? = null, val nombre: String = "", val fecha: String = "")

data class AgendaVacunasState(
    val agenda: AgendaVacunasDto? = null,
    val editor: VacunaEnEdicion? = null,
    val ocupado: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
    val errorNombre: String? = null,
    val errorFecha: String? = null,
)

class AgendaVacunasViewModel(private val api: AgendaVacunasApi, private val solicitudId: Long) : ViewModel() {
    private val mutable = MutableStateFlow(AgendaVacunasState())
    val state = mutable.asStateFlow()
    private val orden = compareBy(VacunaProgramadaDto::fecha_programada, VacunaProgramadaDto::id)

    init { cargar() }

    fun cargar() = ejecutar {
        val agenda = api.listar(solicitudId)
        mutable.update { it.copy(agenda = agenda.copy(vacunas = agenda.vacunas.sortedWith(orden))) }
    }

    fun editar(vacuna: VacunaProgramadaDto? = null) {
        if (mutable.value.ocupado) return
        val editor = vacuna?.let { VacunaEnEdicion(it.id, it.nombre, it.fecha_programada) } ?: VacunaEnEdicion()
        mutable.update { it.copy(editor = editor, errorNombre = null, errorFecha = null, error = null, exito = null) }
    }

    fun cambiarNombre(nombre: String) {
        val editor = mutable.value.editor ?: return
        if (!mutable.value.ocupado) mutable.update { it.copy(editor = editor.copy(nombre = nombre), errorNombre = null) }
    }

    fun cambiarFecha(fecha: String) {
        val editor = mutable.value.editor ?: return
        if (!mutable.value.ocupado) mutable.update { it.copy(editor = editor.copy(fecha = fecha), errorFecha = null) }
    }

    fun cancelarEdicion() {
        if (!mutable.value.ocupado) mutable.update { it.copy(editor = null, errorNombre = null, errorFecha = null, error = null) }
    }

    fun guardar() {
        if (mutable.value.ocupado) return
        val agenda = mutable.value.agenda ?: return
        val editor = mutable.value.editor ?: return
        val nombre = editor.nombre.trim()
        val errorNombre = if (nombre.length !in 1..MAX_NOMBRE_VACUNA) "Ingresa entre 1 y 120 caracteres." else null
        val errorFecha = if (!fechaValida(editor.fecha)) "Ingresa una fecha válida con el formato AAAA-MM-DD." else null
        if (errorNombre != null || errorFecha != null) {
            mutable.update { it.copy(errorNombre = errorNombre, errorFecha = errorFecha) }
            return
        }
        ejecutar {
            val datos = DatosVacunaProgramada(nombre, editor.fecha)
            val guardada = if (editor.id == null) api.crear(solicitudId, datos) else api.editar(solicitudId, editor.id, datos)
            val vacunas = (agenda.vacunas.filterNot { it.id == guardada.id } + guardada).sortedWith(orden)
            mutable.update { it.copy(agenda = agenda.copy(vacunas = vacunas), editor = null, exito = "Vacuna guardada.") }
        }
    }

    fun eliminar(id: Long) {
        val agenda = mutable.value.agenda ?: return
        ejecutar {
            api.eliminar(solicitudId, id)
            val vacunas = agenda.vacunas.filterNot { it.id == id }
            mutable.update { it.copy(agenda = agenda.copy(vacunas = vacunas), editor = null, exito = "Vacuna retirada.") }
        }
    }

    private fun ejecutar(operacion: suspend () -> Unit) {
        if (mutable.value.ocupado) return
        mutable.update { it.copy(ocupado = true, error = null, exito = null) }
        viewModelScope.launch {
            try {
                operacion()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val codigo = (error as? HttpException)?.code() ?: 0
                val mensaje = when (codigo) {
                    400 -> "Revisa el nombre y que la fecha programada sea de hoy o posterior."
                    in 500..599 -> "No se pudo completar la operación en Petly. Intenta nuevamente."
                    else -> apiErrorMessage(error)
                }
                mutable.update { actual ->
                    if (codigo == 401 || codigo == 403 || codigo == 404) AgendaVacunasState(error = mensaje)
                    else actual.copy(error = mensaje)
                }
            } finally {
                mutable.update { it.copy(ocupado = false) }
            }
        }
    }
}

private fun fechaValida(fecha: String): Boolean {
    if (!fecha.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
    val formato = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    return runCatching { formato.parse(fecha) != null }.getOrDefault(false)
}
