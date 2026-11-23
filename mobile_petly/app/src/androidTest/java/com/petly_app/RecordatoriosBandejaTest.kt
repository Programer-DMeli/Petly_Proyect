package com.petly_app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.petly_app.data.remote.NotificacionDto
import com.petly_app.ui.perfil.NotificacionesScreen
import com.petly_app.ui.perfil.NotificacionesState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordatoriosBandejaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun elAvisoDeTresDiasAbreSuReporte() {
        comprobarAviso("RECORDATORIO_REPORTE", 17L, "Próxima entrega de reporte")
    }

    @Test fun elAvisoDeUnDiaAbreSuReporte() {
        comprobarAviso("REPORTE_1_DIA", 24L, "Reporte vence en un día")
    }

    private fun comprobarAviso(tipo: String, seguimientoId: Long, titulo: String) {
        val aviso = NotificacionDto(
            id = 42, solicitud_id = 8, mascota_id = 5, mascota_nombre = "Luna", albergue_nombre = "Huellitas",
            titulo = titulo, mensaje = "Envía el reporte de Luna antes del vencimiento.", descripcion = "",
            estado_actual = "ENTREGADA", ocurrido_en = "2026-12-07T17:00:00Z", leido_en = null,
            tipo = tipo, seguimiento_id = seguimientoId, fecha_limite = "2026-12-10T17:00:00Z",
        )
        val leidos = mutableListOf<Long>()
        val reportes = mutableListOf<Long>()
        val otrosDestinos = mutableListOf<Long>()
        compose.setContent {
            NotificacionesScreen(
                state = NotificacionesState(items = listOf(aviso), noLeidas = 1),
                onRefresh = {}, onRead = { leidos.add(it) }, onReadAll = {}, onMore = {},
                onOpenPet = { otrosDestinos.add(it) }, onOpenSeguimiento = { otrosDestinos.add(it) },
                onOpenReporte = { reportes.add(it) },
            )
        }
        compose.onNodeWithText(titulo).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fecha límite: 10/12/2026 12:00 (hora de Lima)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abrir reporte").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(42L), leidos)
            assertEquals(listOf(seguimientoId), reportes)
            assertEquals(emptyList<Long>(), otrosDestinos)
        }
    }
}
