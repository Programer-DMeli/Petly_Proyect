package com.petly_app.core

import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.AuthApi
import com.petly_app.data.remote.HealthApi
import com.petly_app.data.remote.UsuarioApi
import com.petly_app.data.repository.AuthRepository
import com.petly_app.data.repository.HealthRepository
import com.petly_app.data.repository.UsuarioRepository

// Dependencias compartidas de la aplicación; no se crean al recomponer una pantalla.
class AppContainer {
    val session = SessionManager()
    private val publicApi = ApiClient.publicApi()
    private val privateApi = ApiClient.authenticatedApi(session)
    val healthRepository = HealthRepository(publicApi.create(HealthApi::class.java))
    val authRepository = AuthRepository(publicApi.create(AuthApi::class.java), session)
    val usuarioRepository = UsuarioRepository(privateApi.create(UsuarioApi::class.java), session)
}
