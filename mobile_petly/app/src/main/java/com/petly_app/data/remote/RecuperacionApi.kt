package com.petly_app.data.remote
import com.petly_app.data.model.*
import retrofit2.http.*
interface RecuperacionApi {
    @POST("api/auth/password/reset") suspend fun request(@Body data: RecuperacionRequest): Map<String,String>
    @POST("api/auth/password/reset/confirm") suspend fun confirm(@Body data: ResetRequest): Map<String,String>
}
