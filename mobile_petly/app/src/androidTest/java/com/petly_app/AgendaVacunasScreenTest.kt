package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.petly_app.data.remote.AgendaVacunasApi
import com.petly_app.data.remote.AgendaVacunasDto
import com.petly_app.data.remote.DatosVacunaProgramada
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.data.remote.VacunaProgramadaDto
import com.petly_app.ui.perfil.MisSolicitudesAdopcion
import com.petly_app.ui.seguimiento.AgendaVacunasScreen
import com.petly_app.ui.seguimiento.AgendaVacunasViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AgendaVacunasScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun elAccesoDesdeMisSolicitudesSeOfreceSoloParaLaAdopcionEntregada() {
        var solicitudAbierta: Long? = null
        ui.setContent {
            MaterialTheme {
                MisSolicitudesAdopcion(onOpenAgendaVacunas = { solicitudAbierta = it }, listarSolicitudes = {
                    listOf(SolicitudPersistida(42, 1, "ENTREGADA", "Nube", null, albergue = 7),
                        SolicitudPersistida(43, 2, "APROBADA", "Luna", null))
                })
            }
        }
        ui.onNodeWithText("Entregada").assertExists()
        ui.onNodeWithText("Aprobada").assertExists()
        ui.onNodeWithText("Descargar ficha veterinaria (PDF)").assertExists()
        ui.onNodeWithText("Opiniones y reseñas").assertDoesNotExist()
        ui.onAllNodesWithText("Agenda de vacunas").assertCountEquals(1)
        ui.onNodeWithText("Agenda de vacunas").performClick()
        ui.runOnIdle { assertEquals(42L, solicitudAbierta) }
    }

    @Test fun muestraCargaErrorYAgendaVaciaTrasElReintento() {
        val api = AgendaDePrueba()
        val pendiente = CompletableDeferred<Unit>()
        api.alListar = { pendiente.await(); throw IOException() }
        lateinit var vm: AgendaVacunasViewModel
        ui.runOnUiThread { vm = AgendaVacunasViewModel(api, 42) }
        ui.setContent { MaterialTheme { AgendaVacunasScreen(vm, {}) } }
        ui.onNodeWithText("Cargando agenda…").assertExists()
        pendiente.complete(Unit)
        ui.onNodeWithText("No se pudo conectar con Petly.", substring = true).assertExists()
        ui.runOnIdle { api.alListar = {} }
        ui.onNodeWithText("Reintentar").performClick()
        ui.onNodeWithText("Todavía no hay vacunas programadas.").assertExists()
    }

    @Test fun agregaEditaYRetiraUnaVacunaYRecuperaLosCambiosAlReabrir() {
        val api = AgendaDePrueba()
        val vm = mutableStateOf<AgendaVacunasViewModel?>(null)
        ui.runOnUiThread { vm.value = AgendaVacunasViewModel(api, 42) }
        ui.setContent { MaterialTheme { vm.value?.let { AgendaVacunasScreen(it, {}) } } }

        ui.onNodeWithText("Agregar vacuna").performClick()
        ui.onNodeWithText("Guardar vacuna").performScrollTo().performClick()
        ui.onNodeWithText("Ingresa una fecha válida", substring = true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Nombre de la vacuna").performScrollTo().performTextInput("Rabia")
        ui.onNodeWithText("Fecha programada").performScrollTo().performTextInput("2030-12-02")
        ui.onNodeWithText("Guardar vacuna").performScrollTo().performClick()
        ui.onNodeWithText("Vacuna guardada.").assertExists()
        ui.onNodeWithText("02/12/2030").assertExists()

        ui.runOnIdle { vm.value = AgendaVacunasViewModel(api, 42) }
        ui.onNodeWithText("Rabia").assertExists()
        ui.onNodeWithText("Editar", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("2030-12-02").assertExists()
        ui.onNodeWithText("Nombre de la vacuna").performScrollTo().performTextReplacement("Rabia, refuerzo")
        ui.onNodeWithText("Guardar vacuna").performScrollTo().performClick()
        ui.onNodeWithText("Vacuna guardada.").assertExists()

        ui.runOnIdle { vm.value = AgendaVacunasViewModel(api, 42) }
        ui.onNodeWithText("Rabia, refuerzo").assertExists()
        ui.onNodeWithText("Quitar", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Quitar vacuna", substring = false).performClick()
        ui.onNodeWithText("Vacuna retirada.").assertExists()

        ui.runOnIdle { vm.value = AgendaVacunasViewModel(api, 42) }
        ui.onNodeWithText("Todavía no hay vacunas programadas.").assertExists()
    }

    private class AgendaDePrueba : AgendaVacunasApi {
        private var vacuna: VacunaProgramadaDto? = null
        var alListar: suspend () -> Unit = {}

        override suspend fun listar(solicitud: Long): AgendaVacunasDto {
            alListar()
            return AgendaVacunasDto("Nube", listOfNotNull(vacuna))
        }

        override suspend fun crear(solicitud: Long, datos: DatosVacunaProgramada) = editar(solicitud, 1, datos)

        override suspend fun editar(solicitud: Long, vacuna: Long, datos: DatosVacunaProgramada) =
            VacunaProgramadaDto(vacuna, datos.nombre, datos.fecha_programada).also { this.vacuna = it }

        override suspend fun eliminar(solicitud: Long, vacuna: Long) { this.vacuna = null }
    }
}
