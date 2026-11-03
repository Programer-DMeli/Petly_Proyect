package com.petly_app

import com.petly_app.data.model.PerfilDraft
import com.petly_app.data.model.UserDto
import com.petly_app.data.model.completionPercent
import com.petly_app.data.repository.PerfilRepository
import com.petly_app.ui.perfil.PerfilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {
    private val complete = PerfilDraft(vivienda = "Casa", horasFuera = "4", presupuesto = "200", tieneNinos = false, tieneOtrasMascotas = false)

    @Test fun resumenUsaLaCuentaActivaYConservaRespuestasAlActualizar() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : PerfilRepository {
                override suspend fun load() = complete
                override suspend fun save(draft: PerfilDraft) {}
            }
            fun model(id: Long, email: String, name: String, count: Int) = PerfilViewModel(repo, false,
                loadUser = { UserDto(id, email, rol = "ADOPTANTE", firstName = name) },
                loadApplications = { count }, loadVerified = { false })
            val first = model(1, "ana@petly.test", "Ana", 3)
            val second = model(2, "luis@petly.test", "Luis", 0)
            first.refreshSummary(); second.refreshSummary(); advanceUntilIdle()
            assertEquals("Ana", first.state.value.user?.firstName)
            assertEquals("luis@petly.test", second.state.value.user?.email)
            assertEquals(0, second.state.value.applications)
            first.edit(complete.copy(presupuesto = "350"))
            first.refreshSummary(); advanceUntilIdle()
            assertEquals("350", first.state.value.form.presupuesto)
            assertTrue(first.state.value.dirty)
            assertEquals(100, first.state.value.form.completionPercent())
            assertEquals(0, PerfilDraft().completionPercent())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun finalizarSoloPublicaExitoDespuesDePersistir() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail = true
        try {
            val repo = object : PerfilRepository {
                override suspend fun load() = complete
                override suspend fun save(draft: PerfilDraft) {
                    if (fail) throw java.io.IOException("Sin conexión")
                }
            }
            val vm = PerfilViewModel(repo, false)
            advanceUntilIdle()
            vm.save(true); advanceUntilIdle()
            assertEquals(0L, vm.state.value.saveRevision)
            assertFalse(vm.state.value.form.finalizado)
            assertNotNull(vm.state.value.error)
            fail = false
            vm.save(true); advanceUntilIdle()
            assertEquals(1L, vm.state.value.saveRevision)
            assertTrue(vm.state.value.form.finalizado)
            assertFalse(vm.state.value.dirty)
            vm.edit(PerfilDraft()); vm.save(true); advanceUntilIdle()
            assertEquals(1L, vm.state.value.saveRevision)
            assertTrue(vm.state.value.errors.isNotEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
