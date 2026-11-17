package com.petly_app.data.remote

import com.petly_app.data.model.LoginResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

internal data class SessionRequest(val refreshToken: String, val requestId: String? = null)

internal interface SessionApi {
    @POST("api/auth/refresh")
    fun refresh(@Body request: SessionRequest): Call<LoginResponse>

    @POST("api/auth/logout")
    fun logout(@Body request: SessionRequest): Call<Unit>
}
