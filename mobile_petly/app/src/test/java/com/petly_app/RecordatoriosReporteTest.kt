package com.petly_app

import com.google.gson.JsonObject
import com.petly_app.data.remote.RecordatorioReporteDto
import com.petly_app.data.remote.ReporteBienestarDto
import com.petly_app.data.remote.ReportesSeguimientoApi
import com.petly_app.data.remote.SeguimientoDto
import com.petly_app.data.repository.cruzarPlazos
import com.petly_app.ui.perfil.fechaLimiteLima
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RecordatoriosReporteTest {
    @Test fun ocultaReportesEntregadosAunqueSpringTodaviaLosDevuelvaComoPendientes() {
        val fecha = "2026-11-10T12:00:00"
        val avisos = (1L..6).map { RecordatorioReporteDto(it, fecha, "PENDIENTE") }
        val activo = SeguimientoDto(1, 7, "Luna", "Huellitas", fecha, "VISITA", "PROGRAMADO")
        val disponibles = cruzarPlazos(avisos, listOf(
            activo,
            activo.copy(id = 2, reporte_bienestar = ReporteBienestarDto(20)),
            activo.copy(id = 3, estado = "CANCELADO"),
            activo.copy(id = 4, expediente_cierre = JsonObject()),
            activo.copy(id = 5, estado = "REALIZADO")
        ))
        assertEquals(listOf(1L, 5L), disponibles.map { it.seguimiento.id })
        assertEquals("10/11/2026 12:00 (hora de Lima)", fechaLimiteLima(disponibles.first().fechaLimite))
    }

    @Test fun consultaPendientesYEnviaElReporteDelSeguimientoPorMultipart() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ReportesSeguimientoApi::class.java)
            server.enqueue(MockResponse().setBody("""[{"seguimiento_id":8,"fecha_limite":"2026-11-10T12:00:00","estado":"ENVIADO"}]"""))
            assertEquals(8L, api.pendientes().single().seguimiento_id)
            assertEquals("/api/seguimientos/adoptante/recordatorios?pendientes=true", server.takeRequest().path)

            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":77}"""))
            val foto = MultipartBody.Part.createFormData("fotos", "luna.png", "imagen".toRequestBody())
            assertEquals(77L, api.enviar("8".toRequestBody(), "BUENO".toRequestBody(),
                "Se adapta bien".toRequestBody(), listOf(foto)).id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/seguimientos/adoptante", request.path)
            val body = request.body.readUtf8()
            for (part in listOf("seguimiento_id", "estado_bienestar", "observaciones", "fotos")) {
                assertTrue(body.contains("name=\"$part\""))
            }
            assertTrue(body.contains("BUENO"))
            assertTrue(body.contains("luna.png"))
        }
    }
}
