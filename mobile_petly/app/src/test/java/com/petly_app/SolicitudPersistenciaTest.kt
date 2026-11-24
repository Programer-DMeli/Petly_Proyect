package com.petly_app

import com.google.gson.JsonParser
import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.remote.*
import com.petly_app.data.repository.SolicitudAdopcionManager
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SolicitudPersistenciaTest {
    @Test fun requiereAutorizacionYPermiteReintentarElEnvio() = runBlocking {
        val draft = SolicitudAdopcionDraft(
            mascotaId = 7, mascotaNombre = "Domi", nombreCompleto = "Prueba Android",
            dni = "00000000", direccionHogar = "Lima", telefonoContacto = "999000111",
            motivoAdopcion = "Adopción", tieneAlergias = false, vivesConPersonasEspeciales = false,
            haCuidadoMascotasAntes = true, aceptaEsterilizacion = true, aceptaSeguimiento = true,
            puedeCubrirGastosVeterinarios = true
        )
        val mensaje = "Se requiere autorización vigente para este albergue."
        var autorizado = false
        SolicitudAdopcionManager.clear()
        try {
            SolicitudAdopcionManager.api = object : SolicitudesApi {
                override suspend fun limiteImagen() = error("No hay fotos para consultar el límite")

                override suspend fun crear(datos: CrearSolicitud): SolicitudPersistida {
                    if (!autorizado) {
                        val body = """{"autorizacion":["$mensaje"]}""".toResponseBody()
                        throw HttpException(Response.error<SolicitudPersistida>(400, body))
                    }
                    assertEquals(draft, datos.respuestasCuestionario)
                    return SolicitudPersistida(25, draft.mascotaId, "PENDIENTE", draft.mascotaNombre, draft)
                }

                override suspend fun listar(page: Int) = error("No se solicitó el listado")
                override suspend fun seguimiento(id: Long) = error("No se solicitó el historial")
            }

            val errores = SolicitudAdopcionManager.enviarSolicitud(draft)
            assertEquals(mensaje, errores["autorizacion"])
            assertEquals(mensaje, errores["servidor"])
            assertNull(SolicitudAdopcionManager.identificador(draft.mascotaId))
            assertFalse(SolicitudAdopcionManager.isSolicitudEnviada(draft.mascotaId))

            autorizado = true
            assertTrue(SolicitudAdopcionManager.enviarSolicitud(draft).isEmpty())
            assertEquals(25L, SolicitudAdopcionManager.identificador(draft.mascotaId))
            assertTrue(SolicitudAdopcionManager.isSolicitudEnviada(draft.mascotaId))
        } finally {
            SolicitudAdopcionManager.clear()
        }
    }

    @Test fun recuperaTodasLasPaginasYNormalizaSolicitudesWebSinCuestionario() = runBlocking {
        val pages=mutableListOf<Int>()
        SolicitudAdopcionManager.clear()
        try {
            SolicitudAdopcionManager.api=object:SolicitudesApi {
                override suspend fun limiteImagen() = error("No hay fotos para consultar el límite")

                override suspend fun crear(datos: CrearSolicitud): SolicitudPersistida = error("No debe crear solicitudes al recuperar")
                override suspend fun seguimiento(id: Long): HistorialSolicitud = error("No se solicitó el historial")
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
