package com.petly_app.data.remote

import com.petly_app.data.model.SolicitudAdopcionDraft
import retrofit2.http.*

data class CrearSolicitud(val mascotaId: Long, val mensajePostulante: String, val respuestasCuestionario: SolicitudAdopcionDraft)
data class SolicitudPersistida(
    val id: Long,
    val mascota: Long,
    val estado: String,
    val mascota_nombre: String?,
    val respuestas_cuestionario: SolicitudAdopcionDraft?,
    val albergue: Long? = null
)
data class HistorialSolicitud(val estadoActual: String, val eventos: List<EventoSolicitud>)
data class EventoSolicitud(val id: Long, val tipo_display: String, val descripcion: String?)
interface SolicitudesApi : LimiteImagenApi {
    @POST("api/solicitudes") suspend fun crear(@Body datos: CrearSolicitud): SolicitudPersistida
    @GET("api/solicitudes") suspend fun listar(@Query("page") page: Int = 1): com.google.gson.JsonElement
    @GET("api/solicitudes/{id}/seguimiento") suspend fun seguimiento(@Path("id") id: Long): HistorialSolicitud
}

interface LimiteImagenApi {
    @GET("api/configuracion/limite-imagen")
    suspend fun limiteImagen(): LimiteImagenDto
}

data class LimiteImagenDto(val maxImagenMb: Int)

interface FotosHogarApi {
    @Multipart @POST("api/acceso/solicitudes/{id}/fotos-hogar/")
    suspend fun subir(@Path("id") id: Long, @Part archivo: okhttp3.MultipartBody.Part): Map<String, Long>
    @GET("api/acceso/solicitudes/{id}/fotos-hogar/") suspend fun listar(@Path("id") id: Long): List<FotoHogarDto>
    @GET suspend fun descargar(@Url url: String): okhttp3.ResponseBody
}
data class FotoHogarDto(val id: Long, val url: String, val tipo: String)

interface FichaVeterinariaApi {
    @Streaming
    @GET("api/acceso/solicitudes/{id}/ficha-veterinaria/")
    suspend fun descargar(@Path("id") id: Long): okhttp3.ResponseBody
}
