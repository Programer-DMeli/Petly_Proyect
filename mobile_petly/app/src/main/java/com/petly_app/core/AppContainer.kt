package com.petly_app.core

import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
import android.content.Context
import com.petly_app.core.session.KeystoreRefreshTokenStore
import com.petly_app.core.session.SessionUnavailable
import retrofit2.HttpException

// Dependencias compartidas de la aplicación; no se crean al recomponer una pantalla.
class AppContainer(context: Context) {
    private val contentResolver = context.contentResolver
    private val publicApi = ApiClient.publicApi()
    private val sessionApi = publicApi.create(SessionApi::class.java)
    val session = SessionManager(
        KeystoreRefreshTokenStore(context),
        renew = { credential ->
            val response = sessionApi.refresh(SessionRequest(credential.token, credential.requestId)).execute()
            if (!response.isSuccessful) throw HttpException(response)
            response.body() ?: throw SessionUnavailable()
        },
        revoke = { credential ->
            val response = sessionApi.logout(SessionRequest(credential.token)).execute()
            if (!response.isSuccessful && response.code() != 401) throw HttpException(response)
        },
        now = android.os.SystemClock::elapsedRealtime
    )
    private val privateApi = ApiClient.authenticatedApi(session)
    val healthRepository = HealthRepository(publicApi.create(HealthApi::class.java))
    val authRepository = AuthRepository(publicApi.create(AuthApi::class.java), session)
    val chatRepository: ChatRepository = RemoteChatRepository(privateApi.create(ChatApi::class.java))
    val usuarioRepository = UsuarioRepository(privateApi.create(UsuarioApi::class.java), session)
    private val previewPerfil = PreviewPerfilRepository()
    private val previewCatalogo = PreviewCatalogoRepository()
    private val remotePerfil = RemotePerfilRepository(privateApi.create(PerfilApi::class.java))
    private val remoteCatalogo = RemoteCatalogoRepository(privateApi.create(CatalogoApi::class.java))
    private val previewDisponibilidad = PreviewDisponibilidadRepository()
    private val remoteDisponibilidad = RemoteDisponibilidadRepository(privateApi.create(DisponibilidadApi::class.java))

    val kycRepository = KycRepository(
        ApiClient.authenticatedAdminApi(session).create(KycApi::class.java),
        privateApi.create(KycConsultaApi::class.java), contentResolver
    )

    fun perfil(preview: Boolean): PerfilRepository = if (preview) previewPerfil else remotePerfil
    fun catalogo(preview: Boolean): CatalogoRepository = if (preview) previewCatalogo else remoteCatalogo
    fun disponibilidad(preview: Boolean): DisponibilidadRepository = if (preview) previewDisponibilidad else remoteDisponibilidad
    private val documentosApi = ApiClient.authenticatedAdminApi(session)
    val fotosHogarApi = documentosApi.create(FotosHogarApi::class.java)
    val fichaVeterinariaApi = documentosApi.create(FichaVeterinariaApi::class.java)
    val resolver = contentResolver
    val favoritosApi = privateApi.create(FavoritosApi::class.java)
    val solicitudesApi = privateApi.create(SolicitudesApi::class.java)
    val resenasApi = privateApi.create(ResenasApi::class.java)
    val seguimientosApi = privateApi.create(SeguimientosApi::class.java)
    val agendaVacunasApi = ApiClient.authenticatedAdminApi(session).create(AgendaVacunasApi::class.java)
    val reportesSeguimiento = ReportesSeguimientoRepository(
        seguimientosApi, privateApi.create(ReportesSeguimientoApi::class.java), contentResolver
    )
    val notificacionesApi = ApiClient.authenticatedAdminApi(session).create(NotificacionesApi::class.java)
    val mensajeriaApi = ApiClient.authenticatedAdminApi(session).create(MensajeriaApi::class.java)
    val autorizaciones = AutorizacionRepository(privateApi.create(AutorizacionApi::class.java))
    val recuperacion = RecuperacionRepository(publicApi.create(RecuperacionApi::class.java), { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.clear() } })
    fun resetPreview() = previewPerfil.reset()
}
