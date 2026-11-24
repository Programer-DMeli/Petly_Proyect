package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CrearResena(val calificacion: Int, val comentario: String)
data class EstadoResena(val registrada: Boolean, val calificacion: Int?)

interface ResenasApi {
    @GET("api/albergues/{albergueId}/resenas/mi-resena")
    suspend fun estado(@Path("albergueId") albergueId: Long): EstadoResena

    @POST("api/albergues/{albergueId}/resenas")
    suspend fun crear(@Path("albergueId") albergueId: Long, @Body resena: CrearResena)
}
