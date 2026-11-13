package com.petly_app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class AsesoriaVeterinariaDto(
    val id: Long,
    val seguimiento: Long,
    val problema: String,
    val creado_en: String,
    val estado: String = "PENDIENTE"
)

data class SolicitarAsesoriaDto(val seguimiento: Long, val problema: String)

data class SeguimientoDto(
    val id: Long,
    val solicitud_id: Long,
    val mascota_nombre: String,
    val albergue_nombre: String,
    val programado_para: String,
    val modalidad: String,
    val estado: String,
    val asesorias_veterinarias: List<AsesoriaVeterinariaDto> = emptyList(),
    val reporte_bienestar: ReporteBienestarDto? = null,
    val expediente_cierre: com.google.gson.JsonObject? = null,
    val requiere_reporte: Boolean? = null,
    val estado_entrega: String? = null
)

data class SeguimientosPagina(val results: List<SeguimientoDto>, val next: String?)

interface SeguimientosApi {
    @GET("api/seguimientos/adoptante")
    suspend fun listar(@Query("page") page: Int = 1): SeguimientosPagina

    @POST("api/seguimientos/adoptante/asesorias")
    suspend fun solicitarAsesoria(@Body solicitud: SolicitarAsesoriaDto): AsesoriaVeterinariaDto
}


data class RecordatorioReporteDto(
    val seguimiento_id: Long,
    val fecha_limite: String,
    val anticipacion_dias: Int,
    val estado: String
)
data class ReporteBienestarDto(val id: Long)

interface ReportesSeguimientoApi {
    @GET("api/seguimientos/adoptante/recordatorios?pendientes=true")
    suspend fun pendientes(): List<RecordatorioReporteDto>

    @Multipart
    @POST("api/seguimientos/adoptante")
    suspend fun enviar(
        @Part("seguimiento_id") seguimiento: RequestBody,
        @Part("estado_bienestar") estado: RequestBody,
        @Part("observaciones") observaciones: RequestBody,
        @Part fotos: List<MultipartBody.Part>
    ): ReporteBienestarDto
}

suspend fun SeguimientosApi.listarTodos(): List<SeguimientoDto> = buildList {
    var page = 1
    do {
        val response = listar(page++)
        addAll(response.results)
    } while (response.next != null)
}
