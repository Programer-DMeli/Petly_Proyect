package com.petly_app.data.remote

import retrofit2.Response
import retrofit2.http.GET

interface HealthApi {
    @GET("api/health")
    suspend fun check(): Response<Unit>
}

