package com.petly_app.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

data class ConversacionDto(
    val id: Long,
    @SerializedName("solicitud_id") val solicitudId: Long?,
    val mascota: String,
    @SerializedName("nombre_contacto") val nombreContacto: String,
    @SerializedName("no_leidos") val noLeidos: Int,
    @SerializedName("promedio_respuesta_horas") val promedioRespuestaHoras: Double?,
    @SerializedName("ultimo_mensaje") val ultimoMensaje: MensajeDto?,
    val bloqueada: Boolean = false,
    @SerializedName("puede_enviar") val puedeEnviar: Boolean = true
)
data class AdjuntoMensajeDto(val id: Long, @SerializedName("nombre_original") val nombreOriginal: String, val descarga: String)
data class MensajeDto(val id: Long, @SerializedName("remitente_id") val remitenteId: Long, val texto: String,
    @SerializedName("creado_en") val creadoEn: String, val adjuntos: List<AdjuntoMensajeDto> = emptyList(),
    val propio: Boolean = false, val leido: Boolean = false,
    @SerializedName("bienvenida_automatica") val bienvenidaAutomatica: Boolean = false)
data class ConversacionDetalleDto(val conversacion: ConversacionDto, val mensajes: List<MensajeDto>)
data class ActualizacionesMensajesDto(
    val mensajes: List<MensajeDto>,
    @SerializedName("leidos_hasta_id") val leidosHastaId: Long?
)

interface MensajeriaApi {
    @GET("api/mensajeria/conversaciones/") suspend fun listar(): List<ConversacionDto>
    @POST("api/mensajeria/conversaciones/") suspend fun crear(@Body solicitud: Map<String, Long>): ConversacionDto
    @GET("api/mensajeria/conversaciones/{id}/") suspend fun detalle(@Path("id") id: Long): ConversacionDetalleDto
    @GET("api/mensajeria/conversaciones/{id}/mensajes/")
    suspend fun nuevosMensajes(@Path("id") id: Long, @Query("despues_de_id") despuesDeId: Long): ActualizacionesMensajesDto
    @Multipart @POST("api/mensajeria/conversaciones/{id}/mensajes/")
    suspend fun enviar(@Path("id") id: Long, @Part("texto") texto: RequestBody?, @Part archivo: MultipartBody.Part?): MensajeDto
    @POST("api/mensajeria/conversaciones/{id}/leer/")
    suspend fun marcarLeido(@Path("id") id: Long, @Body body: Map<String, Long>)
    @POST("api/mensajeria/conversaciones/{id}/bloquear/") suspend fun bloquear(@Path("id") id: Long, @Body body: Map<String, String> = emptyMap())
    @POST("api/mensajeria/conversaciones/{id}/reportar/") suspend fun reportar(@Path("id") id: Long, @Body body: Map<String, String>)
    @GET suspend fun descargar(@Url url: String): okhttp3.ResponseBody
}
