package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.data.model.Mascota
import com.petly_app.data.remote.ActualizacionesMensajesDto
import com.petly_app.data.remote.ConversacionDetalleDto
import com.petly_app.data.remote.ConversacionDto
import com.petly_app.data.remote.MensajeDto
import com.petly_app.data.remote.MensajeriaApi
import com.petly_app.data.remote.MensajesNoLeidosDto
import com.petly_app.ui.catalogo.DetalleMascotaScreen
import com.petly_app.ui.catalogo.DetalleMascotaUiState
import com.petly_app.ui.mensajes.MensajesScreen
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ContactoAlbergueTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contactarAbreElChatDelAlbergueYPermiteReintentar() {
        val pet = Mascota(1, "Luna", "Perro", "Mediano", 24, "Sociable", "Media", "",
            "Refugio de prueba", "Lima", "Disponible", albergueId = 42L)
        val target = mutableStateOf<Long?>(null)
        val api = ApiDePrueba()
        compose.setContent {
            MaterialTheme {
                val shelterId = target.value
                if (shelterId == null) {
                    DetalleMascotaScreen(DetalleMascotaUiState(mascota = pet), false, {}, {},
                        onContactShelter = { target.value = it })
                } else {
                    MensajesScreen(api, LocalContext.current.contentResolver, emptyList(), {},
                        initialShelterId = shelterId, onBack = { target.value = null })
                }
            }
        }

        compose.onNodeWithText("Contactar").performScrollTo().performClick()
        compose.onNodeWithText("No se pudo abrir el chat del albergue.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Refugio de prueba").assertIsDisplayed()
        compose.onNodeWithText("Bienvenida del refugio").assertIsDisplayed()
        compose.onNodeWithText("Escribe un mensaje").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(List(2) { mapOf("albergue_id" to 42L) }, api.requests)
        }
        compose.onNodeWithText("← Volver a la mascota").performClick()
        compose.onNodeWithText("Contactar").performScrollTo().assertIsDisplayed()
    }

    private class ApiDePrueba : MensajeriaApi {
        val requests = mutableListOf<Map<String, Long>>()
        private val chat = ConversacionDto(12, null, "Consulta general", "Refugio de prueba", 0, null, null)
        private val welcome = MensajeDto(3, 2, "Bienvenida del refugio", "2026-11-24T10:00:00Z")

        override suspend fun crear(solicitud: Map<String, Long>): ConversacionDto {
            requests += solicitud
            if (requests.size == 1) throw IOException("Fallo simulado")
            return chat
        }

        override suspend fun listar() = listOf(chat)
        override suspend fun detalle(id: Long): ConversacionDetalleDto {
            assertEquals(chat.id, id)
            return ConversacionDetalleDto(chat, listOf(welcome))
        }
        override suspend fun nuevosMensajes(id: Long, despuesDeId: Long) = ActualizacionesMensajesDto(emptyList(), null)
        override suspend fun contarNoLeidos() = MensajesNoLeidosDto(0)
        override suspend fun marcarLeido(id: Long, body: Map<String, Long>) = Unit
        override suspend fun enviar(id: Long, texto: RequestBody?, archivo: MultipartBody.Part?): MensajeDto = error("Inesperado")
        override suspend fun bloquear(id: Long, body: Map<String, String>) = error("Inesperado")
        override suspend fun desbloquear(id: Long) = error("Inesperado")
        override suspend fun reportar(id: Long, body: Map<String, String>) = error("Inesperado")
        override suspend fun descargar(url: String) = error("Inesperado")
    }
}
