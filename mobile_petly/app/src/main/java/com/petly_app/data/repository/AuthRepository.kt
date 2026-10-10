package com.petly_app.data.repository

import com.petly_app.core.network.ContractException
import com.petly_app.core.network.AdoptanteOnlyException
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.LoginRequest
import com.petly_app.data.model.RegisterRequest
import com.petly_app.data.remote.AuthApi
import retrofit2.HttpException

class AuthRepository(
    private val api: AuthApi,
    private val session: SessionManager
) {
    suspend fun register(email: String, password: String) {
        val response = api.register(RegisterRequest(email.trim(), password))
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun login(email: String, password: String) {
        val result = api.login(LoginRequest(email.trim(), password))
        val token = result.getEffectiveToken()?.takeIf { it.isNotBlank() }
            ?: throw ContractException()
        val expiresIn = result.getEffectiveExpiresIn().takeIf { it in 1..604800 } ?: 86400L

        try {
            val user = api.me("Bearer $token")
            val role = user.getEffectiveRole()
            if (role != null && !role.contains("ADOPTANTE", ignoreCase = true) && !role.contains("USER", ignoreCase = true)) {
                throw AdoptanteOnlyException()
            }
        } catch (e: Exception) {
            if (e is AdoptanteOnlyException) throw e
            // Si /api/auth/me no está presente o falla en el backend, no bloqueamos la sesión si el login fue exitoso.
        }
        session.start(token, expiresIn)
    }
}

