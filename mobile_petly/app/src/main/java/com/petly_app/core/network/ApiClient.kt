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

    fun authenticatedApi(session: SessionManager): Retrofit =
        authenticated(BuildConfig.API_BASE_URL, session)

    fun authenticatedAdminApi(session: SessionManager): Retrofit =
        authenticated(BuildConfig.ADMIN_API_BASE_URL, session)

    private fun authenticated(baseUrl: String, session: SessionManager): Retrofit {
        val client = baseClient.newBuilder()
            .addInterceptor { chain ->
                val auth = session.accessForRequest()
                val original = chain.request()
                val builder = original.newBuilder()
                if (auth != null) builder.header("Authorization", "Bearer ${auth.token}")
                val path = original.url.encodedPath
                val activeChain = if (path.endsWith("/explicacion") || path == "/api/asistente/chat") {
                    chain.withReadTimeout(65, TimeUnit.SECONDS)
                } else {
                    chain
                }
                val response = activeChain.proceed(builder.build())
                if (response.code != 401 || auth == null || chain.call().isCanceled()) return@addInterceptor response
                val renewed = try {
                    session.accessForRequest(rejected = auth)
                } catch (error: java.io.IOException) {
                    response.close()
                    throw error
                }
                if (renewed == null || original.method !in setOf("GET", "HEAD")) return@addInterceptor response
                response.close()
                activeChain.proceed(builder.header("Authorization", "Bearer ${renewed.token}").build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().serializeNulls().create()))
            .build()
    }
}
