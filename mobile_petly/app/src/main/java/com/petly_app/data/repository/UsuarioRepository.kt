package com.petly_app.data.repository

import com.petly_app.core.network.ContractException
import com.petly_app.core.network.AdoptanteOnlyException
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.UserDto
import com.petly_app.data.remote.UsuarioApi

class UsuarioRepository(
    private val api: UsuarioApi,
    private val session: SessionManager
) {
    suspend fun currentUser(): UserDto {
        val user = api.me()
        if (user.id == null || user.email.isNullOrBlank() || user.getEffectiveRole().isNullOrBlank()) {
            throw ContractException()
        }
        if (user.getEffectiveRole() != "ADOPTANTE") {
            session.clear()
            throw AdoptanteOnlyException()
        }
        return user
    }
    fun logout() = session.clear()
}
