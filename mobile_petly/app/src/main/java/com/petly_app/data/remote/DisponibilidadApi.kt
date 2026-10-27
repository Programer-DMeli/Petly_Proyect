package com.petly_app.data.remote

import com.petly_app.data.model.FranjaHoraria
import retrofit2.http.*

interface DisponibilidadApi {
    @GET("api/perfil/disponibilidad")
    suspend fun list(): List<FranjaHoraria>

    @POST("api/perfil/disponibilidad")
    suspend fun save(@Body franjas: List<FranjaHoraria>): List<FranjaHoraria>

    @DELETE("api/perfil/disponibilidad/{id}")
    suspend fun delete(@Path("id") id: Long)
}
