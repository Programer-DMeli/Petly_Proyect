package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
    val asesorias_veterinarias: List<AsesoriaVeterinariaDto> = emptyList()
)

data class SeguimientosPagina(val results: List<SeguimientoDto>, val next: String?)

interface SeguimientosApi {
    @GET("api/seguimientos/adoptante")
    suspend fun listar(@Query("page") page: Int = 1): SeguimientosPagina

    @POST("api/seguimientos/adoptante/asesorias")
    suspend fun solicitarAsesoria(@Body solicitud: SolicitarAsesoriaDto): AsesoriaVeterinariaDto
}
