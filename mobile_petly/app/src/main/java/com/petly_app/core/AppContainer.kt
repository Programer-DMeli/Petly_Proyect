package com.petly_app.core

import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
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
    private val remotePerfil = RemotePerfilRepository(privateApi.create(PerfilApi::class.java))
    private val remoteCatalogo = RemoteCatalogoRepository(privateApi.create(CatalogoApi::class.java))

    fun perfil(preview: Boolean): PerfilRepository = if (preview) previewPerfil else remotePerfil
    fun catalogo(preview: Boolean): CatalogoRepository = if (preview) previewCatalogo else remoteCatalogo
    val autorizaciones = AutorizacionRepository(privateApi.create(AutorizacionApi::class.java))
    val recuperacion = RecuperacionRepository(publicApi.create(RecuperacionApi::class.java))
    fun resetPreview() = previewPerfil.reset()
}
