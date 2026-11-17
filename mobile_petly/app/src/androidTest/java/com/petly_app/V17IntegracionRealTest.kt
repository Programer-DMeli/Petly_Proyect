package com.petly_app

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.LoginRequest
import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.remote.AuthApi
import com.petly_app.data.remote.MensajeriaApi
import com.petly_app.data.remote.SessionApi
import com.petly_app.data.remote.SessionRequest
import com.petly_app.data.repository.SolicitudAdopcionManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class V17IntegracionRealTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>(effectContext = Dispatchers.Main.immediate)

    @Test fun loginSolicitudYContadorDeChatContraLosBackends() = runBlocking<Unit> {
        val fixtureFile = File(ui.activity.filesDir, "petly-v17-fixtures.json")
        assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".integration") &&
            BuildConfig.API_BASE_URL == "http://10.0.2.2:8082/" &&
            BuildConfig.ADMIN_API_BASE_URL == "http://10.0.2.2:8002/" && fixtureFile.exists())
        val fixture = JSONObject(fixtureFile.readText())
        val adoptante = fixture.getJSONObject("ADOPTANTE")
        val albergue = fixture.getJSONObject("ALBERGUE")
        val mascotaId = fixture.getLong("mascotaAndroid")
        val mascota = "Integración S2 V17 Android"
        val container = (ui.activity.application as PetlyApplication).container
        container.session.logout()
        val public = ApiClient.publicApi()
        val loginAlbergue = public.create(AuthApi::class.java).login(
            LoginRequest(albergue.getString("correo"), albergue.getString("password"))
        )
        val sessionAlbergue = SessionManager().apply {
            start(requireNotNull(loginAlbergue.token), 3600, requireNotNull(loginAlbergue.user?.id))
        }
        val mensajesAlbergue = ApiClient.authenticatedAdminApi(sessionAlbergue).create(MensajeriaApi::class.java)
        try {
            ui.onNodeWithTag("login_email").performTextInput(adoptante.getString("correo"))
            ui.onNodeWithTag("login_password").performTextInput(adoptante.getString("password"))
            ui.onNodeWithTag("login_submit").performClick()
            esperarTexto("Catálogo")
            ui.onNodeWithText("Catálogo", useUnmergedTree = true).performClick()
            esperarTexto(mascota)
            ui.onNodeWithText(mascota).performClick()
            ui.waitUntil(20_000) {
                ui.onAllNodes(SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription, listOf("Quiero Adoptar a $mascota")
                )).fetchSemanticsNodes().isNotEmpty()
            }

            ui.runOnIdle {
                SolicitudAdopcionManager.saveDraft(SolicitudAdopcionDraft(
                    mascotaId = mascotaId, mascotaNombre = mascota, nombreCompleto = "Adoptante de prueba",
                    dni = "00000000", direccionHogar = "Lima", telefonoContacto = "999000111",
                    motivoAdopcion = "Solicitud sintética Android V17", tieneAlergias = false,
                    vivesConPersonasEspeciales = false, haCuidadoMascotasAntes = true,
                    aceptaEsterilizacion = true, aceptaSeguimiento = true, puedeCubrirGastosVeterinarios = true
                ))
            }
            ui.onNodeWithContentDescription("Quiero Adoptar a $mascota")
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            esperarTexto("Solicitud de Adopción de $mascota")
            ui.onNodeWithText("Enviar solicitud de adopción")
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            esperarTexto("Mis solicitudes de adopción")
            esperarTexto(mascota)
            val solicitud = SolicitudAdopcionManager.listarSolicitudes().single { it.mascota == mascotaId }
            assertEquals("PENDIENTE", solicitud.estado)

            val chat = mensajesAlbergue.crear(mapOf("solicitud_id" to solicitud.id))
            val primerMensaje = "Albergue a Android, V17"
            val respuesta = "Android al albergue, V17"
            val mensajePosterior = "Actualización por sondeo en Android, V17"
            val adoptanteApi = container.mensajeriaApi
            assertEquals(0, adoptanteApi.contarNoLeidos().total)
            mensajesAlbergue.enviar(chat.id, primerMensaje.toRequestBody("text/plain".toMediaType()), null)
            val badge = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 mensaje sin leer")
            ui.waitUntil(18_000) { ui.onAllNodes(badge).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Mensajes", useUnmergedTree = true).performClick()
            val cardName = "Albergue Integración V17 · $mascota"
            esperarTexto(cardName)
            ui.onNodeWithText(cardName).performClick()
            esperarTexto(primerMensaje)
            ui.waitUntil(18_000) { ui.onAllNodes(badge).fetchSemanticsNodes().isEmpty() }
            assertEquals(0, adoptanteApi.contarNoLeidos().total)
            ui.onNodeWithText("Escribe un mensaje").performTextInput(respuesta)
            ui.onNodeWithText("Enviar", substring = false)
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            esperarTexto("Escribe un mensaje")
            esperarTexto(respuesta)
            mensajesAlbergue.enviar(chat.id, mensajePosterior.toRequestBody("text/plain".toMediaType()), null)
            esperarTexto(mensajePosterior)
            val history = mensajesAlbergue.detalle(chat.id).mensajes.map { it.texto }
            assertTrue(history.containsAll(listOf(primerMensaje, respuesta, mensajePosterior)))
            Log.i("PetlyV17E2E", "Android: solicitud persistida, chat bidireccional, sondeo y contador 0→1→0.")
        } finally {
            container.session.logout()
            loginAlbergue.refreshToken?.let {
                public.create(SessionApi::class.java).logout(SessionRequest(it)).execute().errorBody()?.close()
            }
        }
    }

    private fun esperarTexto(texto: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(texto, useUnmergedTree = true).assertIsDisplayed()
    }
}
