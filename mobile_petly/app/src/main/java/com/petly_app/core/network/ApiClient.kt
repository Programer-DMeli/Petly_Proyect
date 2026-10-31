package com.petly_app.core.network

import android.util.Log
import com.petly_app.BuildConfig
import com.petly_app.core.session.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        Log.d("ApiClient", "--> ${request.method} ${request.url.encodedPath}")
                        val response = chain.proceed(request)
                        Log.d("ApiClient", "<-- ${response.code} ${request.url.encodedPath}")
                        response
                    }
                }
            }
            .build()
    }

    fun publicApi(): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(baseClient)
        .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().serializeNulls().create()))
        .build()

    fun authenticatedApi(session: SessionManager): Retrofit {
        val authClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val token = session.token()
                val original = chain.request()
                val request = original.newBuilder().apply {
                    if (token != null) header("Authorization", "Bearer $token")
                }.build()
                val activeChain = if (original.url.encodedPath.endsWith("/explicacion")) chain.withReadTimeout(65, TimeUnit.SECONDS) else chain
                val response = activeChain.proceed(request)
                if (response.code == 401 && token != null) {
                    session.clearIfMatches(token)
                }
                response
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().serializeNulls().create()))
            .build()
    }

    fun authenticatedAdminApi(session: SessionManager): Retrofit {
        val authClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val token = session.token()
                val request = chain.request().newBuilder().apply {
                    if (token != null) header("Authorization", "Bearer $token")
                }.build()
                val response = chain.proceed(request)
                if (response.code == 401 && token != null) session.clearIfMatches(token)
                response
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.ADMIN_API_BASE_URL)
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().serializeNulls().create()))
            .build()
    }
}


