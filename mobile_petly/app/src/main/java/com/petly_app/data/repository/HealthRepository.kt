package com.petly_app.data.repository

import com.petly_app.data.remote.HealthApi
import retrofit2.HttpException

class HealthRepository(private val api: HealthApi) {
    suspend fun check() {
        val response = api.check()
        if (!response.isSuccessful) throw HttpException(response)
    }
}
