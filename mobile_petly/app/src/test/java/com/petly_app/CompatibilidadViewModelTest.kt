package com.petly_app

import com.petly_app.data.model.*
import com.petly_app.data.repository.CatalogoRepository
import com.petly_app.ui.catalogo.DetalleMascotaViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompatibilidadViewModelTest {
    private fun pet() = Mascota(1, "Luna", "Perro", "Grande", 24, "Sociable", "Alta", "Pelaje marrón", "Refugio", "Lima", "Disponible", compatibilidad = Compatibilidad(100, 100, 100, false, "Orientativo", "petly-compat-v1", listOf(
        CriterioCompatibilidad("vivienda", "Vivienda", 30, 100, 30.0, "Vivienda conocida"),
        CriterioCompatibilidad("rutina", "Rutina", 35, 100, 35.0, "Rutina conocida"),
        CriterioCompatibilidad("convivencia", "Convivencia", 35, 100, 35.0, "Convivencia conocida"))))

    @Test fun falloYReintentoIANoOcultanNiCambianDesglose() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        val animal = pet()
        val repo = object : CatalogoRepository {
            override suspend fun options() = CatalogoOptions(emptyList(), emptyList(), emptyList())
            override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int) = MascotaPage(listOf(animal), page, 1, pageSize)
            override suspend fun detail(id: Long) = animal
            override suspend fun consejos(id: Long): ConsejosIA {
                calls++
                if (calls == 1) throw java.io.IOException("Servicio no disponible")
                return ConsejosIA("DISPONIBLE", "Consejo para revisión", listOf("Coordina paseos"))
            }
        }
        try {
            val vm = DetalleMascotaViewModel(repo, 1)
            advanceUntilIdle()
            vm.consejos(); advanceUntilIdle()
            assertNull(vm.state.value.error)
            assertEquals("NO_DISPONIBLE", vm.state.value.consejos?.estado)
            assertEquals(animal, vm.state.value.mascota)
            vm.consejos(); advanceUntilIdle()
            assertEquals("DISPONIBLE", vm.state.value.consejos?.estado)
            assertEquals(100, vm.state.value.mascota?.compatibilidad?.porcentaje)
            assertEquals(3, vm.state.value.mascota?.compatibilidad?.criterios?.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun recargarDetalleDescartaConsejoObsoletoMientrasLlegaRespuestaAnterior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = object : CatalogoRepository {
            override suspend fun options() = CatalogoOptions(emptyList(), emptyList(), emptyList())
            override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int) = MascotaPage(emptyList(), page, 0, pageSize)
            override suspend fun detail(id: Long) = pet()
            override suspend fun consejos(id: Long): ConsejosIA { delay(1000); return ConsejosIA("DISPONIBLE", "Obsoleto") }
        }
        try {
            val vm = DetalleMascotaViewModel(repo, 1); advanceUntilIdle()
            vm.consejos(); runCurrent(); vm.load(); advanceUntilIdle()
            assertNull(vm.state.value.consejos)
            assertFalse(vm.state.value.consultandoIA)
            assertEquals(100, vm.state.value.mascota?.compatibilidad?.porcentaje)
        } finally { Dispatchers.resetMain() }
    }
}
