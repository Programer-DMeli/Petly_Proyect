package com.petly_app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.petly_app.data.remote.CrearResena
import com.petly_app.data.remote.EstadoResena
import com.petly_app.data.remote.HistorialSolicitud
import com.petly_app.data.remote.ResenasApi
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.ui.perfil.MisSolicitudesAdopcion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ResenaAlbergueScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun habilitaLaResenaTrasElCierreYRecuperaElEstadoAlVolverAlPerfil() {
        var cerradoEn: String? = null
        val api = object : ResenasApi {
            var resena: CrearResena? = null
            var consultas = 0

            override suspend fun estado(albergueId: Long): EstadoResena {
                assertEquals(7L, albergueId)
                consultas++
                return EstadoResena(resena != null, resena?.calificacion)
            }

            override suspend fun crear(albergueId: Long, resena: CrearResena) {
                assertEquals(7L, albergueId)
                checkNotNull(cerradoEn)
                check(this.resena == null)
                this.resena = resena
            }
        }
        val visitaPerfil = mutableIntStateOf(0)
        ui.setContent {
            MaterialTheme {
                key(visitaPerfil.intValue) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MisSolicitudesAdopcion(
                            listarSolicitudes = { listOf(SolicitudPersistida(42, 1, "ENTREGADA", "Nube", null, albergue = 7)) },
                            resenasApi = api,
                            consultarHistorial = { HistorialSolicitud("ENTREGADA", emptyList(), 7, cerradoEn) },
                        )
                    }
                }
            }
        }
        ui.onNodeWithText("Ver seguimiento").performClick()
        ui.onNodeWithText("Aún no hay eventos de seguimiento.").assertExists()
        ui.onNodeWithText("Opiniones y reseñas").assertDoesNotExist()
        ui.onNodeWithText("Cerrar").performClick()

        ui.runOnIdle { cerradoEn = "2026-11-24T10:15:00-05:00" }
        ui.onNodeWithText("Ver seguimiento").performClick()
        ui.onNodeWithText("Cierre de adopción validado").assertExists()
        ui.onNodeWithText("Opiniones y reseñas").performClick()
        ui.onNodeWithText("Publicar reseña").assertIsNotEnabled()
        ui.onNodeWithContentDescription("4 estrellas").performClick()
        ui.onNodeWithText("Tu opinión").performTextInput("Atención amable.")
        ui.onNodeWithText("Publicar reseña").performClick()
        ui.onNodeWithText("Opiniones y reseñas").assertDoesNotExist()
        ui.onNodeWithText("Cerrar").performClick()
        ui.onNodeWithText("Reseña registrada").assertExists()

        var consultasPrevias = 0
        ui.runOnIdle {
            assertEquals(CrearResena(4, "Atención amable."), api.resena)
            consultasPrevias = api.consultas
            visitaPerfil.intValue++
        }
        ui.onNodeWithText("Reseña registrada").assertExists()
        ui.runOnIdle { check(api.consultas > consultasPrevias) }
    }
}
