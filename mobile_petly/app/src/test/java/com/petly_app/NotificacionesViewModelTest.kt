package com.petly_app

import com.petly_app.data.remote.*
import com.petly_app.ui.perfil.NotificacionesViewModel
import com.petly_app.ui.perfil.notificationDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class NotificacionesViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi : NotificacionesApi {
        val pages = mutableListOf<Int>()
        var rows = (1L..3L).map { id -> NotificacionDto(id, 10, 20, "Luna", "Huellitas",
            "Solicitud aprobada", "Tu solicitud fue aprobada", "", "APROBADA", "2026-11-05T05:10:00Z", null) }
        var failRead = false
        override suspend fun listar(page: Int): NotificacionesPagina {
            pages.add(page)
            return NotificacionesPagina(rows.drop((page - 1) * 2).take(2),
                if (page == 1) "?page=2" else null, rows.count { it.leido_en == null })
        }
        override suspend fun leer(id: Long): NotificacionDto {
            if (failRead) throw java.io.IOException()
            rows = rows.map { if (it.id == id) it.copy(leido_en = "2026-11-05T05:11:00Z") else it }
            return rows.first { it.id == id }
        }
        override suspend fun leerTodas() { rows = rows.map { it.copy(leido_en = "2026-11-05T05:11:00Z") } }
    }

    @Test fun conservaPaginasDuranteActualizacionYNoDuplicaAvisos() = runTest {
        val api = FakeApi()
        val vm = NotificacionesViewModel(api)
        vm.load(); advanceUntilIdle()
        vm.load(true); advanceUntilIdle()
        assertEquals(3, vm.state.value.items.size)
        vm.load(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 1, 2), api.pages)
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        vm.load(true); advanceUntilIdle()
        assertEquals(4, api.pages.size)
    }

    @Test fun lecturaConfirmadaSeConservaYElContadorNoDisminuyeDosVeces() = runTest {
        val api = FakeApi()
        val vm = NotificacionesViewModel(api)
        vm.load(); advanceUntilIdle()
        vm.read(1); advanceUntilIdle()
        vm.read(1); advanceUntilIdle()
        assertEquals(2, vm.state.value.noLeidas)
        vm.load(); advanceUntilIdle()
        assertNotNull(vm.state.value.items.first().leido_en)
        vm.readAll(); advanceUntilIdle()
        assertEquals(0, vm.state.value.noLeidas)
        assertTrue(vm.state.value.items.all { it.leido_en != null })
    }

    @Test fun errorDeLecturaPermiteReintentarSinPerderAvisos() = runTest {
        val api = FakeApi()
        val vm = NotificacionesViewModel(api)
        vm.load(); advanceUntilIdle()
        api.failRead = true
        vm.read(1); advanceUntilIdle()
        assertEquals(3, vm.state.value.noLeidas)
        assertNull(vm.state.value.items.first().leido_en)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        api.failRead = false
        vm.read(1); advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.noLeidas)
    }

    @Test fun previewNoConsultaDatosPrivados() = runTest {
        val api = FakeApi()
        val vm = NotificacionesViewModel(api, preview = true)
        vm.load(); vm.read(1); vm.readAll(); advanceUntilIdle()
        assertTrue(api.pages.isEmpty())
        assertTrue(vm.state.value.preview)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun fechaUtcSeMuestraEnHoraLocalConYSinMicrosegundos() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
            assertEquals("05/11/2026 00:10", notificationDate("2026-11-05T05:10:00.123456Z"))
            assertEquals("05/11/2026 00:10", notificationDate("2026-11-05T05:10:00Z"))
        } finally { TimeZone.setDefault(previous) }
    }
}
