package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.petly_app.data.remote.NotificacionDto
import com.petly_app.ui.perfil.NotificacionesScreen
import com.petly_app.ui.perfil.NotificacionesState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificacionesScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun muestraDecisionYMotivoYPermiteMarcarComoLeida() {
        val aviso = NotificacionDto(1, 42, 10, "Luna", "Huellitas", "Solicitud observada",
            "El albergue necesita que revises las observaciones de tu solicitud.",
            "Confirma el horario para la entrevista.", "OBSERVADA", "2026-11-05T05:00:00Z", null)
        val state = mutableStateOf(NotificacionesState(items = listOf(aviso), noLeidas = 1))
        ui.setContent {
            MaterialTheme {
                NotificacionesScreen(state.value, {}, { id ->
                    assertEquals(1L, id)
                    state.value = state.value.copy(items = listOf(aviso.copy(leido_en = "2026-11-05T05:01:00Z")), noLeidas = 0)
                }, {}, {})
            }
        }
        ui.onNodeWithText("1 sin leer").assertIsDisplayed()
        ui.onNodeWithText("Nueva · Solicitud observada").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Luna · Huellitas").assertIsDisplayed()
        ui.onNodeWithText("Observaciones: Confirma el horario para la entrevista.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Marcar como leída").performScrollTo().performClick()
        ui.onNodeWithText("Solicitud observada").assertExists()
        ui.onNodeWithText("Marcar como leída").assertDoesNotExist()
        ui.onNodeWithText("0 sin leer").performScrollTo().assertIsDisplayed()
    }

    @Test fun avisoDeCoincidenciaAbrePerfilSinMostrarSolicitud() {
        var opened: Long? = null
        var read: Long? = null
        val aviso = NotificacionDto(2, null, 25, "Luna", "Huellitas", "Nueva mascota para ti",
            "Una nueva mascota coincide con tus criterios guardados.", "", "DISPONIBLE", "2026-11-05T05:00:00Z", null, "NUEVA_COINCIDENCIA")
        ui.setContent {
            MaterialTheme {
                NotificacionesScreen(NotificacionesState(items = listOf(aviso)), {}, { read = it }, {}, {}, onOpenPet = { opened = it })
            }
        }
        ui.onNodeWithText("Solicitud #", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Nueva · Nueva mascota para ti").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(25L, opened); assertEquals(2L, read) }
    }

    @Test fun errorDeRedOfreceReintentoYNoMuestraFalsoEstadoVacio() {
        var retries = 0
        ui.setContent {
            MaterialTheme {
                NotificacionesScreen(NotificacionesState(error = "No se pudo conectar."),
                    { retries++ }, {}, {}, {})
            }
        }
        ui.onNodeWithText("No se pudo conectar.").assertIsDisplayed()
        ui.onNodeWithText("Reintentar").performClick()
        ui.runOnIdle { assertEquals(1, retries) }
        ui.onNodeWithText("Todavía no tienes notificaciones.", substring = true).assertDoesNotExist()
    }
}
