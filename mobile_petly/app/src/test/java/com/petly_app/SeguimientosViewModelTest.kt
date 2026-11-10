package com.petly_app

import com.petly_app.data.remote.*
import com.petly_app.ui.seguimiento.SeguimientosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SeguimientosViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi : SeguimientosApi {
        val pages = mutableListOf<Int>()
        val requests = mutableListOf<SolicitarAsesoriaDto>()
        var failLoad = false
        var failSubmit = false
        val rows = listOf(
            SeguimientoDto(1, 10, "Luna", "Huellitas", "2026-11-09T12:00:00Z", "LLAMADA", "PROGRAMADO"),
            SeguimientoDto(2, 10, "Luna", "Huellitas", "2026-11-10T12:00:00Z", "VISITA", "CANCELADO")
        )
        override suspend fun listar(page: Int): SeguimientosPagina {
            pages.add(page)
            if (failLoad) throw IOException()
            return SeguimientosPagina(listOf(rows[page - 1]), if (page == 1) "?page=2" else null)
        }
        override suspend fun solicitarAsesoria(solicitud: SolicitarAsesoriaDto): AsesoriaVeterinariaDto {
            requests.add(solicitud)
            if (failSubmit) throw IOException()
            return AsesoriaVeterinariaDto(5, solicitud.seguimiento, solicitud.problema, "2026-11-08T12:00:00Z")
        }
    }

    @Test fun cargaTodasLasPaginasYEnviaProblemaAlSeguimientoSeleccionadoUnaVez() = runTest {
        val api = FakeApi()
        val vm = SeguimientosViewModel(api)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), api.pages)
        vm.select(1)
        vm.edit("  Llora cuando se queda sola.  ")
        vm.submit(); vm.submit()
        advanceUntilIdle()
        assertEquals(listOf(SolicitarAsesoriaDto(1, "Llora cuando se queda sola.")), api.requests)
        assertNotNull(vm.state.value.success)
        assertNull(vm.state.value.selectedId)
        assertEquals("", vm.state.value.problema)
        assertEquals(1, vm.state.value.items.first().asesorias_veterinarias.size)
        assertEquals("PENDIENTE", vm.state.value.items.first().asesorias_veterinarias.single().estado)
        assertTrue(vm.state.value.items.last().asesorias_veterinarias.isEmpty())
    }

    @Test fun noEnviaProblemasVaciosNiDemasiadoLargosNiSeguimientosCancelados() = runTest {
        val api = FakeApi()
        val vm = SeguimientosViewModel(api)
        advanceUntilIdle()
        vm.select(2)
        assertNull(vm.state.value.selectedId)
        vm.select(1)
        for (problema in listOf("   ", "x".repeat(2001))) {
            vm.edit(problema); vm.submit(); advanceUntilIdle()
            assertNotNull(vm.state.value.fieldError)
        }
        assertTrue(api.requests.isEmpty())
    }

    @Test fun conservaElProblemaAnteFalloYPermiteReintentar() = runTest {
        val api = FakeApi()
        val vm = SeguimientosViewModel(api)
        advanceUntilIdle()
        vm.select(1); vm.edit("No quiere comer en su nuevo hogar.")
        api.failSubmit = true
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertEquals(1L, vm.state.value.selectedId)
        assertEquals("No quiere comer en su nuevo hogar.", vm.state.value.problema)
        assertNull(vm.state.value.success)
        assertTrue(vm.state.value.items.first().asesorias_veterinarias.isEmpty())
        api.failSubmit = false
        vm.submit(); advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.success)
    }

    @Test fun errorDeCargaPermiteActualizarSinFingirExito() = runTest {
        val api = FakeApi().apply { failLoad = true }
        val vm = SeguimientosViewModel(api)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        api.failLoad = false
        vm.load(); advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.items.size)
    }

    @Test fun previewNoConsultaNiEnviaDatos() = runTest {
        val api = FakeApi()
        val vm = SeguimientosViewModel(api, preview = true)
        vm.load(); vm.select(1); vm.edit("Problema"); vm.submit(); advanceUntilIdle()
        assertTrue(api.pages.isEmpty())
        assertTrue(api.requests.isEmpty())
        assertTrue(vm.state.value.preview)
    }
}
