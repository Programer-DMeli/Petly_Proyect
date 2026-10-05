package com.petly_app.data.remote

import com.petly_app.data.model.LoginRequest
import com.petly_app.data.model.LoginResponse
import com.petly_app.data.model.RegisterRequest
import com.petly_app.data.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// Rutas propuestas. Ajustar a los controllers reales de Persona B.
interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(@Header("Authorization") authorization: String): UserDto
}
