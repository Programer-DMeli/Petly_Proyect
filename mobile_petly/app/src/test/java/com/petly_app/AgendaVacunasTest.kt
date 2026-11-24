package com.petly_app

import com.google.gson.JsonParser
import com.petly_app.data.remote.AgendaVacunasApi
import com.petly_app.data.remote.AgendaVacunasDto
import com.petly_app.data.remote.DatosVacunaProgramada
import com.petly_app.data.remote.VacunaProgramadaDto
import com.petly_app.ui.seguimiento.AgendaVacunasViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaVacunasTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun limpiar() { Dispatchers.resetMain() }

    @Test fun guardaOrdenaEditaYQuitaVacunasAlReabrir() = runTest(dispatcher) {
        val api = AgendaDePrueba()
        val vm = AgendaVacunasViewModel(api, 42)
        assertTrue(vm.state.value.ocupado)
        advanceUntilIdle()
        assertTrue(vm.state.value.agenda!!.vacunas.isEmpty())

        for ((nombre, fecha) in listOf("Refuerzo" to "2030-12-08", "  Rabia  " to "2030-12-01")) {
            vm.editar()
            vm.cambiarNombre(nombre)
            vm.cambiarFecha(fecha)
            vm.guardar()
            advanceUntilIdle()
            assertEquals("Vacuna guardada.", vm.state.value.exito)
        }
        assertEquals(listOf("Rabia", "Refuerzo"), vm.state.value.agenda!!.vacunas.map { it.nombre })

        val reabierta = AgendaVacunasViewModel(api, 42)
        advanceUntilIdle()
        reabierta.editar(reabierta.state.value.agenda!!.vacunas.last())
        assertEquals("2030-12-08", reabierta.state.value.editor!!.fecha)
        reabierta.cambiarNombre("Refuerzo actualizado")
        reabierta.cambiarFecha("2030-11-25")
        reabierta.guardar()
        advanceUntilIdle()
        assertEquals("Refuerzo actualizado", reabierta.state.value.agenda!!.vacunas.first().nombre)
        reabierta.eliminar(reabierta.state.value.agenda!!.vacunas.last().id)
        advanceUntilIdle()
        assertEquals("Vacuna retirada.", reabierta.state.value.exito)

        val despues = AgendaVacunasViewModel(api, 42)
        advanceUntilIdle()
        assertEquals(listOf("Refuerzo actualizado"), despues.state.value.agenda!!.vacunas.map { it.nombre })
        assertEquals(listOf(42L, 42L, 42L), api.solicitudesConsultadas)
    }

    @Test fun validaCamposMuestraErrorYPermiteReintentar() = runTest(dispatcher) {
        val api = AgendaDePrueba().apply { error = IOException() }
        val vm = AgendaVacunasViewModel(api, 42)
        advanceUntilIdle()
        assertNull(vm.state.value.agenda)
        assertTrue(vm.state.value.error!!.contains("conexión"))
        api.error = null
        vm.cargar()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        vm.editar()
        vm.cambiarFecha("2030-02-30")
        vm.guardar()
        assertNotNull(vm.state.value.errorNombre)
        assertNotNull(vm.state.value.errorFecha)
        assertTrue(api.vacunas.isEmpty())

        vm.cambiarNombre("Triple felina")
        vm.cambiarFecha("2030-12-01")
        api.error = IOException()
        vm.guardar()
        advanceUntilIdle()
        assertEquals("Triple felina", vm.state.value.editor!!.nombre)
        assertTrue(vm.state.value.error!!.contains("conexión"))
        api.error = null
        vm.guardar()
        advanceUntilIdle()
        assertEquals("Triple felina", vm.state.value.agenda!!.vacunas.single().nombre)

        api.error = HttpException(Response.error<String>(403, "{}".toResponseBody()))
        vm.cargar()
        advanceUntilIdle()
        assertNull(vm.state.value.agenda)
        assertTrue(vm.state.value.error!!.contains("permiso"))
    }

    private class AgendaDePrueba : AgendaVacunasApi {
        val vacunas = mutableListOf<VacunaProgramadaDto>()
        val solicitudesConsultadas = mutableListOf<Long>()
        var error: Exception? = null
        private var siguiente = 1L

        override suspend fun listar(solicitud: Long): AgendaVacunasDto {
            error?.let { throw it }
            solicitudesConsultadas += solicitud
            return AgendaVacunasDto("Nube", vacunas.toList())
        }

        override suspend fun crear(solicitud: Long, datos: DatosVacunaProgramada): VacunaProgramadaDto {
            error?.let { throw it }
            return VacunaProgramadaDto(siguiente++, datos.nombre, datos.fecha_programada).also { vacunas += it }
        }

        override suspend fun editar(solicitud: Long, vacuna: Long, datos: DatosVacunaProgramada): VacunaProgramadaDto {
            val editada = VacunaProgramadaDto(vacuna, datos.nombre, datos.fecha_programada)
            vacunas[vacunas.indexOfFirst { it.id == vacuna }] = editada
            return editada
        }

        override suspend fun eliminar(solicitud: Long, vacuna: Long) {
            vacunas.removeAll { it.id == vacuna }
        }
    }
}

class AgendaVacunasApiTest {
    @Test fun enviaLosCuatroMetodosConLaSolicitudYElToken() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer prueba").build())
            }.build()
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(AgendaVacunasApi::class.java)
            val raiz = "/api/acceso/solicitudes/42/vacunas-programadas/"
            val datos = DatosVacunaProgramada("Rabia", "2030-12-01")
            val respuesta = """{"id":5,"nombre":"Rabia","fecha_programada":"2030-12-01"}"""
            server.enqueue(MockResponse().setBody("""{"mascota_nombre":"Nube","vacunas":[$respuesta]}"""))
            assertEquals("Nube", api.listar(42).mascota_nombre)
            server.enqueue(MockResponse().setResponseCode(201).setBody(respuesta))
            assertEquals(5L, api.crear(42, datos).id)
            server.enqueue(MockResponse().setBody(respuesta))
            assertEquals("2030-12-01", api.editar(42, 5, datos).fecha_programada)
            server.enqueue(MockResponse().setResponseCode(204))
            api.eliminar(42, 5)

            for ((metodo, ruta) in listOf("GET" to raiz, "POST" to raiz, "PATCH" to "${raiz}5/", "DELETE" to "${raiz}5/")) {
                val req = server.takeRequest()
                assertEquals(metodo, req.method)
                assertEquals(ruta, req.path)
                assertEquals("Bearer prueba", req.getHeader("Authorization"))
                if (metodo == "POST" || metodo == "PATCH") {
                    val body = JsonParser.parseString(req.body.readUtf8()).asJsonObject
                    assertEquals(datos.nombre, body["nombre"].asString)
                    assertEquals(datos.fecha_programada, body["fecha_programada"].asString)
                    assertEquals(setOf("nombre", "fecha_programada"), body.keySet())
                }
            }
        }
    }
}
