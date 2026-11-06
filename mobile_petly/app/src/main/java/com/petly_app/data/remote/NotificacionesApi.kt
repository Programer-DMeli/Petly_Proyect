package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class NotificacionDto(
    val id: Long,
    val solicitud_id: Long?,
    val mascota_id: Long,
    val mascota_nombre: String,
    val albergue_nombre: String,
    val titulo: String,
    val mensaje: String,
    val descripcion: String,
    val estado_actual: String,
    val ocurrido_en: String,
    val leido_en: String?,
    val tipo: String = ""
)

data class NotificacionesPagina(
    val results: List<NotificacionDto>,
    val next: String?,
    val no_leidas: Int
)

data class CriteriosAvisoDto(
    val activo: Boolean = false,
    val especie: String = "",
    val tamanio: String = "",
    val temperamento: String = "",
    val edad_min_meses: Int? = null,
    val edad_max_meses: Int? = null
)

interface NotificacionesApi {
    @GET("api/acceso/notificaciones/criterios/")
    suspend fun criterios(): CriteriosAvisoDto

    @PUT("api/acceso/notificaciones/criterios/")
    suspend fun guardarCriterios(@Body criterios: CriteriosAvisoDto): CriteriosAvisoDto

    @GET("api/acceso/notificaciones/")
    suspend fun listar(@Query("page") page: Int = 1): NotificacionesPagina

    @POST("api/acceso/notificaciones/{id}/leer/")
    suspend fun leer(@Path("id") id: Long): NotificacionDto

    @POST("api/acceso/notificaciones/leer-todas/")
    suspend fun leerTodas()
}
