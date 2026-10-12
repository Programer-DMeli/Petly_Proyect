package com.petly_app.data.remote
import com.petly_app.data.model.*
import retrofit2.http.*
interface CatalogoApi {
    @GET("api/catalogo/opciones") suspend fun options(): CatalogoOptions
    @GET("api/catalogo") suspend fun search(@Query("search") search: String,
        @Query("especie") especie: String?, @Query("tamano") tamano: String?,
        @Query("temperamento") temperamento: String?, @Query("edadMinimaMeses") min: Int?,
        @Query("edadMaximaMeses") max: Int?, @Query("page") page: Int, @Query("pageSize") size: Int): MascotaPage
    @GET("api/catalogo/{id}") suspend fun detail(@Path("id") id: Long): Mascota
}
