package com.petly_app.core.network

import com.petly_app.BuildConfig
import com.petly_app.core.session.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private fun client() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun publicApi(): Retrofit = retrofit(client().build())

    fun authenticatedApi(session: SessionManager): Retrofit {
        val client = client().addInterceptor { chain ->
            val token = session.token()
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            val response = chain.proceed(request)
            if (response.code == 401 && token != null) session.clearIfMatches(token)
            response
        }.build()
        return retrofit(client)
    }
}
