package com.petly_app.data.remote

import com.petly_app.data.model.KycDto
import com.petly_app.data.model.KycPageDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface KycApi {
    @GET("api/acceso/verificaciones-kyc/")
    suspend fun list(): KycPageDto

    @Multipart
    @POST("api/acceso/verificaciones-kyc/")
    suspend fun upload(
        @Part("dni") dni: RequestBody,
        @Part("nombres") nombres: RequestBody,
        @Part("apellidos") apellidos: RequestBody,
        @Part documentoAnverso: MultipartBody.Part,
        @Part documentoReverso: MultipartBody.Part
    ): KycDto

}

interface KycConsultaApi {
    @POST("api/kyc/verificaciones/{id}/consultar")
    suspend fun consultar(@Path("id") id: Long): com.petly_app.data.model.KycConsultaDto
}
