package com.petly_app.data.model

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String)

// Nullable para detectar campos ausentes o nulos sin aceptar una sesión inválida.
data class LoginResponse(
    val accessToken: String? = null,
    val expiresIn: Long? = null
)

data class UserDto(
    val id: Long? = null,
    val email: String? = null,
    val role: String? = null
)
