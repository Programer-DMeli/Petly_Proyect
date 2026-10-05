package com.petly_app.data.remote

import com.petly_app.data.model.UserDto
import retrofit2.http.GET

interface UsuarioApi {
    @GET("api/auth/me")
    suspend fun me(): UserDto
}
