package com.petly_app.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.petly_app.data.remote.RecordatorioReporteDto
import com.petly_app.data.remote.ReportesSeguimientoApi
import com.petly_app.data.remote.SeguimientoDto
import com.petly_app.data.remote.SeguimientosApi
import com.petly_app.data.remote.listarTodos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

data class PlazoReporte(
    val seguimiento: SeguimientoDto,
    val fechaLimite: String,
    val anticipacionesAviso: Set<Int> = emptySet()
)

class ReportesSeguimientoRepository(
    private val seguimientos: SeguimientosApi,
    private val reportes: ReportesSeguimientoApi,
    private val resolver: ContentResolver
) {
    suspend fun pendientes(): List<PlazoReporte> {
        val cronograma = seguimientos.listarTodos()
        val (actuales, antiguos) = cronograma.partition { it.requiere_reporte != null }
        val avisos = try {
            reportes.pendientes()
        } catch (error: Exception) {
            if (antiguos.isNotEmpty()) throw error
            emptyList()
        }
        val diasPorSeguimiento = avisos.groupBy { it.seguimiento_id }
            .mapValues { (_, recordatorios) -> recordatorios.map { it.anticipacion_dias }.toSet() }
        val plazosActuales = actuales.filter { it.admiteReporte() }
            .map { PlazoReporte(it, it.programado_para, diasPorSeguimiento[it.id].orEmpty()) }
        return (plazosActuales + cruzarPlazos(avisos, antiguos)).distinctBy { it.seguimiento.id }
    }

    suspend fun enviar(id: Long, estado: String, observaciones: String, fotos: List<Uri>) {
        require(fotos.size in 1..5) { "Selecciona entre 1 y 5 fotos." }
        val partes = withContext(Dispatchers.IO) {
            var restantes = 20 * MIB
            fotos.mapIndexed { index, uri ->
                val limite = minOf(5 * MIB, restantes)
                val datos = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(output.size() + count <= limite) {
                            "Cada foto admite hasta 5 MB y el conjunto hasta 20 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("No se pudo abrir una de las fotos seleccionadas.")
                require(datos.isNotEmpty()) { "Una de las fotos seleccionadas está vacía." }
                restantes -= datos.size
                val png = resolver.getType(uri) == "image/png"
                val mime = if (png) "image/png" else "image/jpeg"
                val extension = if (png) "png" else "jpg"
                MultipartBody.Part.createFormData("fotos", "bienestar-${index + 1}.$extension",
                    datos.toRequestBody(mime.toMediaType()))
            }
        }
        val texto = "text/plain".toMediaType()
        reportes.enviar(id.toString().toRequestBody(texto), estado.toRequestBody(texto),
            observaciones.trim().toRequestBody(texto), partes)
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}

internal fun cruzarPlazos(avisos: List<RecordatorioReporteDto>, seguimientos: List<SeguimientoDto>): List<PlazoReporte> {
    val disponibles = seguimientos.filter {
        it.estado in setOf("PROGRAMADO", "REALIZADO") &&
            it.requiere_reporte != false && it.reporte_bienestar == null && it.expediente_cierre == null
    }.associateBy { it.id }
    return avisos.groupBy { it.seguimiento_id }.mapNotNull { (seguimientoId, recordatorios) ->
        disponibles[seguimientoId]?.let { seguimiento ->
            PlazoReporte(
                seguimiento,
                recordatorios.first().fecha_limite,
                recordatorios.map { it.anticipacion_dias }.toSet(),
            )
        }
    }
}

internal fun SeguimientoDto.admiteReporte() =
    expediente_cierre == null && (estado_entrega == "PENDIENTE" || estado_entrega == "VENCIDA")
