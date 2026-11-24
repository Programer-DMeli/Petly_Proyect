package com.petly_app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

private const val HISTORIAS_ADOPCION = "api/contratos/casos-exito/"

data class AdopcionElegibleDto(val id: Long)

data class HistoriaAdopcionDto(
    val solicitud_id: Long,
    val estado: String,
    val estado_display: String,
    val motivo_revision: String?,
)

data class HistoriasAdopcionPagina(val results: List<HistoriaAdopcionDto>, val next: String?)

interface HistoriasAdopcionApi {
    @GET("${HISTORIAS_ADOPCION}adopciones-disponibles/")
    suspend fun adopcionesDisponibles(): List<AdopcionElegibleDto>

    @GET(HISTORIAS_ADOPCION)
    suspend fun listar(@Query("page") page: Int): HistoriasAdopcionPagina

    @Multipart
    @POST(HISTORIAS_ADOPCION)
    suspend fun enviar(
        @Part("solicitud_id") solicitudId: RequestBody,
        @Part("historia") historia: RequestBody,
        @Part fotos: List<MultipartBody.Part>,
    ): HistoriaAdopcionDto
}
