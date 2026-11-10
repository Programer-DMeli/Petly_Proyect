package com.petly_app

import com.petly_app.data.remote.SeguimientosApi
import com.petly_app.data.remote.SolicitarAsesoriaDto
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SeguimientosApiTest {
    @Test fun consultaYEnviaAsesoriaPorSpringConMotivoSeguimientoYEstado() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer prueba").build())
            }.build()
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(SeguimientosApi::class.java)
            server.enqueue(MockResponse().setBody("""{"results":[],"next":null}"""))
            assertTrue(api.listar(2).results.isEmpty())
            val get = server.takeRequest()
            assertEquals("/api/seguimientos/adoptante?page=2", get.path)
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"id":5,"seguimiento":8,"problema":"No come","estado":"PENDIENTE","creado_en":"2026-11-10T12:00:00Z"}"""
            ))
            val saved = api.solicitarAsesoria(SolicitarAsesoriaDto(8, "No come"))
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/api/seguimientos/adoptante/asesorias", post.path)
            assertEquals("Bearer prueba", post.getHeader("Authorization"))
            val body = com.google.gson.JsonParser.parseString(post.body.readUtf8()).asJsonObject
            assertEquals(8L, body["seguimiento"].asLong)
            assertEquals("No come", body["problema"].asString)
            assertEquals(setOf("seguimiento", "problema"), body.keySet())
            assertEquals("PENDIENTE", saved.estado)
        }
    }
}
