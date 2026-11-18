package com.petly_app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.petly_app.ui.theme.Petly_appTheme
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
            Petly_appTheme(darkTheme = false) {
                NotificacionesScreen(state.value, {}, { id ->
                    assertEquals(1L, id)
                    state.value = state.value.copy(items = listOf(aviso.copy(leido_en = "2026-11-05T05:01:00Z")), noLeidas = 0)
                }, {}, {})
            }
        }
        ui.onNodeWithText("1 sin leer").assertIsDisplayed()
        ui.onNodeWithText("Solicitud observada").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Luna").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Huellitas").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Sin leer").assertExists()
        ui.onNodeWithText("1 sin leer").performScrollTo()
        ui.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "notificaciones-diseno.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        ui.onNodeWithText("Confirma el horario para la entrevista.").performScrollTo().assertIsDisplayed()
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
            Petly_appTheme(darkTheme = false) {
                NotificacionesScreen(NotificacionesState(items = listOf(aviso)), {}, { read = it }, {}, {}, onOpenPet = { opened = it })
            }
        }
        ui.onNodeWithText("Solicitud #", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Nueva mascota para ti").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(25L, opened); assertEquals(2L, read) }
    }

    @Test fun errorDeRedOfreceReintentoYNoMuestraFalsoEstadoVacio() {
        var retries = 0
        ui.setContent {
            Petly_appTheme(darkTheme = false) {
                NotificacionesScreen(NotificacionesState(error = "No se pudo conectar."),
                    { retries++ }, {}, {}, {})
            }
        }
        ui.onNodeWithText("No se pudo conectar.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Reintentar").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, retries) }
        ui.onNodeWithText("Todavía no tienes notificaciones.", substring = true).assertDoesNotExist()
    }

    @Test fun textoGrandeEnPantallaPequenaMantieneLasAccionesAccesibles() {
        var opened: Long? = null
        val aviso = NotificacionDto(3, 21, 10, "Luna de nombre largo", "Refugio de mascotas de nombre largo",
            "Revisión veterinaria rechazada", "La mascota no fue aprobada en la revisión veterinaria.",
            "Es necesario completar la ficha veterinaria antes de continuar con la solicitud.",
            "APROBADA", "2026-11-17T13:10:00Z", null, "VETERINARIA_RECHAZADA")
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                Petly_appTheme(darkTheme = false) {
                    Box(Modifier.requiredWidth(320.dp)) {
                        NotificacionesScreen(NotificacionesState(items = listOf(aviso), noLeidas = 1),
                            {}, {}, {}, {}, onOpenPet = { opened = it })
                    }
                }
            }
        }
        ui.onNodeWithText("Revisión veterinaria rechazada").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(aviso.descripcion).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Ver mascota").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(10L, opened) }
        ui.onNodeWithText("Marcar como leída").performScrollTo().assertIsDisplayed()
    }

    @Test fun cargaYBandejaVaciaMuestranEstadosDiferentes() {
        val state = mutableStateOf(NotificacionesState(busy = true))
        ui.setContent {
            Petly_appTheme(darkTheme = false) {
                NotificacionesScreen(state.value, {}, {}, {}, {})
            }
        }
        ui.onNodeWithText("Actualizando notificaciones…").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Tu bandeja está al día").assertDoesNotExist()
        ui.runOnIdle { state.value = NotificacionesState() }
        ui.onNodeWithText("Tu bandeja está al día").performScrollTo().assertIsDisplayed()
    }
}
