package com.petly_app.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("jwt") val jwt: String? = null,
    @SerializedName("jwtToken") val jwtToken: String? = null,
    @SerializedName("expiresIn") val expiresIn: Long? = null,
    @SerializedName("expires_in") val expiresInSnake: Long? = null
) {
    fun getEffectiveToken(): String? = token ?: accessToken ?: jwt ?: jwtToken
    fun getEffectiveExpiresIn(): Long = expiresIn ?: expiresInSnake ?: 86400L
}

data class UserDto(
    val id: Long? = null,
    val email: String? = null,
    val role: String? = null,
    val rol: String? = null
) {
    fun getEffectiveRole(): String? = role ?: rol
}

