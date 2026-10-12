package com.petly_app.data.remote
import com.petly_app.data.model.*
import retrofit2.http.*
interface AutorizacionApi {
    @GET("api/catalogo/albergues") suspend fun albergues(): List<AlbergueDto>
    @GET("api/autorizaciones") suspend fun list(): List<AutorizacionDto>
    @PUT("api/autorizaciones/{id}") suspend fun allow(@Path("id") id: Long): Map<String, Any>
    @DELETE("api/autorizaciones/{id}") suspend fun revoke(@Path("id") id: Long): Map<String, Any>
}
