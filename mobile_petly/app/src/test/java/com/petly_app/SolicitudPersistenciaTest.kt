package com.petly_app

import com.google.gson.JsonParser
import com.petly_app.data.remote.*
import com.petly_app.data.repository.SolicitudAdopcionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SolicitudPersistenciaTest {
    @Test fun recuperaTodasLasPaginasYNormalizaSolicitudesWebSinCuestionario() = runBlocking {
        val pages=mutableListOf<Int>()
        SolicitudAdopcionManager.clear()
        try {
            SolicitudAdopcionManager.api=object:SolicitudesApi {
                override suspend fun crear(datos: CrearSolicitud): SolicitudPersistida = error("No debe crear solicitudes al recuperar")
                override suspend fun listar(page: Int): com.google.gson.JsonElement {
                    pages.add(page)
                    val ids=if(page==1) 1..20 else 21..21
                    val rows=ids.joinToString(",") { """{"id":$it,"mascota":$it,"estado":"PENDIENTE","mascota_nombre":"Mascota $it","respuestas_cuestionario":{}}""" }
                    return JsonParser.parseString("""{"results":[$rows],"next":${if(page==1) "\"/segunda-pagina\"" else "null"}}""")
                }
            }
            SolicitudAdopcionManager.cargar()
            assertEquals(listOf(1,2),pages)
            assertEquals(21,SolicitudAdopcionManager.submitted.value.size)
            assertTrue(SolicitudAdopcionManager.isSolicitudEnviada(21))
            assertEquals("",SolicitudAdopcionManager.getDraft(21,"Mascota 21").nombreCompleto)
        } finally { SolicitudAdopcionManager.clear() }
    }
}
