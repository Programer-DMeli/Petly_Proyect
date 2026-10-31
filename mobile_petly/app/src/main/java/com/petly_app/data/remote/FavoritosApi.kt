package com.petly_app.data.remote
import com.petly_app.data.model.Mascota
import retrofit2.http.*
interface FavoritosApi {
    @GET("api/perfil/favoritos") suspend fun listar(): List<Mascota>
    @PUT("api/perfil/favoritos/{id}") suspend fun agregar(@Path("id") id: Long): retrofit2.Response<Unit>
    @DELETE("api/perfil/favoritos/{id}") suspend fun quitar(@Path("id") id: Long): retrofit2.Response<Unit>
}
