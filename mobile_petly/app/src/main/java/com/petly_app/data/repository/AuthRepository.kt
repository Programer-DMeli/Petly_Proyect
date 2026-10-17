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
    suspend fun register(email: String, password: String, fullName: String = "", acceptedTerms: Boolean = false) {
        // Preserve the supplied full name without guessing culturally dependent surname boundaries.
        val response = api.register(RegisterRequest(email.trim(), password,
            firstName = fullName.trim().takeIf { it.isNotEmpty() }, aceptoTerminos = acceptedTerms))
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun login(email: String, password: String) {
        val result = api.login(LoginRequest(email.trim(), password))
        val token = result.getEffectiveToken()?.takeIf { it.isNotBlank() }
            ?: throw ContractException()
        val expiresIn = result.getEffectiveExpiresIn().takeIf { it in 1..604800 } ?: throw ContractException()

        val user = api.me("Bearer $token")
        if (user.id == null || user.email.isNullOrBlank() || user.getEffectiveRole() != "ADOPTANTE") {
            throw AdoptanteOnlyException()
        }
        session.start(token, expiresIn)
    }
}

