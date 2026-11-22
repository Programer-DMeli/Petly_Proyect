package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

data class CrearResena(val calificacion: Int, val comentario: String)

interface ResenasApi {
    @POST("api/albergues/{albergueId}/resenas")
    suspend fun crear(@Path("albergueId") albergueId: Long, @Body resena: CrearResena)
}
