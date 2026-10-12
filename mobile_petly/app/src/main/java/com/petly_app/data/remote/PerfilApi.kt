package com.petly_app.data.remote
import com.petly_app.data.model.PerfilDto
import retrofit2.http.*
interface PerfilApi {
    @GET("api/perfil") suspend fun load(): PerfilDto
    @PATCH("api/perfil") suspend fun save(@Body dto: PerfilDto): PerfilDto
}
