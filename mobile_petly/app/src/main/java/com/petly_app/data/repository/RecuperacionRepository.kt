package com.petly_app.data.repository
import com.petly_app.data.remote.RecuperacionApi
import com.petly_app.data.model.*
class RecuperacionRepository(private val api: RecuperacionApi) {
    suspend fun request(email: String) { api.request(RecuperacionRequest(email.trim())) }
    suspend fun confirm(token: String, password: String, confirmation: String) { api.confirm(ResetRequest(token.trim(),password,confirmation)) }
}
