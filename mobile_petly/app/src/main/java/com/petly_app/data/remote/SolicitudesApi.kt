package com.petly_app.data.remote

import com.petly_app.data.model.SolicitudAdopcionDraft
import retrofit2.http.*

data class CrearSolicitud(val mascotaId: Long, val mensajePostulante: String, val respuestasCuestionario: SolicitudAdopcionDraft)
data class SolicitudPersistida(val id: Long, val mascota: Long, val estado: String, val mascota_nombre: String?, val respuestas_cuestionario: SolicitudAdopcionDraft?)
interface SolicitudesApi {
    @POST("api/solicitudes") suspend fun crear(@Body datos: CrearSolicitud): SolicitudPersistida
    @GET("api/solicitudes") suspend fun listar(@Query("page") page: Int = 1): com.google.gson.JsonElement
}

interface FotosHogarApi {
    @Multipart @POST("api/acceso/solicitudes/{id}/fotos-hogar/")
    suspend fun subir(@Path("id") id: Long, @Part archivo: okhttp3.MultipartBody.Part): Map<String, Long>
    @GET("api/acceso/solicitudes/{id}/fotos-hogar/") suspend fun listar(@Path("id") id: Long): List<FotoHogarDto>
    @GET suspend fun descargar(@Url url: String): okhttp3.ResponseBody
}
data class FotoHogarDto(val id: Long, val url: String, val tipo: String)
