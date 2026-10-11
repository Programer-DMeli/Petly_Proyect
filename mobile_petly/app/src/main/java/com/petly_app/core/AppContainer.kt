package com.petly_app.core

import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.AuthApi
import com.petly_app.data.remote.HealthApi
import com.petly_app.data.remote.UsuarioApi
import com.petly_app.data.repository.AuthRepository
import com.petly_app.data.repository.HealthRepository
import com.petly_app.data.repository.UsuarioRepository
import com.petly_app.data.repository.PreviewPerfilRepository
import com.petly_app.data.repository.PreviewCatalogoRepository
import com.petly_app.data.repository.PendingPerfilRepository
import com.petly_app.data.repository.PendingCatalogoRepository
import com.petly_app.data.repository.PerfilRepository
import com.petly_app.data.repository.CatalogoRepository

// Dependencias compartidas de la aplicación; no se crean al recomponer una pantalla.
class AppContainer {
    val session = SessionManager()
    private val publicApi = ApiClient.publicApi()
    private val privateApi = ApiClient.authenticatedApi(session)
    val healthRepository = HealthRepository(publicApi.create(HealthApi::class.java))
    val authRepository = AuthRepository(publicApi.create(AuthApi::class.java), session)
    val usuarioRepository = UsuarioRepository(privateApi.create(UsuarioApi::class.java), session)
    private val previewPerfil = PreviewPerfilRepository()
    private val previewCatalogo = PreviewCatalogoRepository()
    private val pendingPerfil = PendingPerfilRepository()
    private val pendingCatalogo = PendingCatalogoRepository()

    fun perfil(preview: Boolean): PerfilRepository = if (preview) previewPerfil else pendingPerfil
    fun catalogo(preview: Boolean): CatalogoRepository = if (preview) previewCatalogo else pendingCatalogo
    fun resetPreview() = previewPerfil.reset()
}
