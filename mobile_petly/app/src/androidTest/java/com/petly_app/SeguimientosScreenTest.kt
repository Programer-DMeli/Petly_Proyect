package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.petly_app.data.remote.SeguimientoDto
import com.petly_app.data.remote.AsesoriaVeterinariaDto
import com.petly_app.ui.seguimiento.SeguimientosScreen
import com.petly_app.ui.seguimiento.SeguimientosState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeguimientosScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun permiteElegirSeguimientoDescribirProblemaYEnviarSolicitudGratuita() {
        val row = SeguimientoDto(8, 10, "Luna", "Huellitas", "2026-11-09T12:00:00Z", "LLAMADA", "PROGRAMADO")
        val state = mutableStateOf(SeguimientosState(items = listOf(row)))
        var sent: Pair<Long?, String>? = null
        ui.setContent {
            MaterialTheme {
                SeguimientosScreen(state.value, {}, { state.value = state.value.copy(selectedId = it) },
                    { state.value = state.value.copy(problema = it) },
                    { sent = state.value.selectedId to state.value.problema }, {})
            }
        }
        ui.onNodeWithText("Luna").assertExists()
        ui.onNodeWithText("Solicitar asesoría veterinaria gratuita").performScrollTo().performClick()
        ui.onNodeWithText("Problema de adaptación").performScrollTo().performTextInput("Llora cuando está sola.")
        ui.onNodeWithText("Enviar solicitud gratuita").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(8L to "Llora cuando está sola.", sent) }
    }

    @Test fun muestraMotivoYEstadoDeLaAsesoriaEnElSeguimiento() {
        val row = SeguimientoDto(8, 10, "Luna", "Huellitas", "2026-11-09T12:00:00Z", "LLAMADA", "PROGRAMADO",
            listOf(AsesoriaVeterinariaDto(3, 8, "No come en casa.", "2026-11-10T12:00:00Z", "EN_ATENCION")))
        ui.setContent {
            MaterialTheme { SeguimientosScreen(SeguimientosState(items = listOf(row)), {}, {}, {}, {}, {}) }
        }
        ui.onNodeWithText("No come en casa.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Estado de asesoría: En atención").performScrollTo().assertIsDisplayed()
    }
}
