package com.petly_app

import com.petly_app.data.remote.MensajeriaApi
import com.petly_app.ui.mensajes.ContadorMensajesNoLeidos
import com.petly_app.ui.mensajes.INTERVALO_MENSAJERIA_MS
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class ContadorMensajesNoLeidosTest {
    @Test fun consultaAlIniciarYRefrescaAlLeerSinEsperarAlSiguienteIntervalo() = runTest {
        var totalServidor = 2
        var consultas = 0
        val contador = ContadorMensajesNoLeidos { consultas++; totalServidor }
        backgroundScope.launch { contador.observar() }
        runCurrent()
        assertEquals(2, contador.total.value)

        totalServidor = 3
        advanceTimeBy(INTERVALO_MENSAJERIA_MS)
        runCurrent()
        assertEquals(3, contador.total.value)

        totalServidor = 0
        contador.actualizar()
        contador.actualizar()
        runCurrent()
        assertEquals(0, contador.total.value)
        assertEquals(3, consultas)
    }

    @Test fun mantieneElUltimoTotalDuranteUnFalloDeRed() = runTest {
        var conectado = true
        val contador = ContadorMensajesNoLeidos {
            if (!conectado) throw IOException()
            4
        }
        backgroundScope.launch { contador.observar() }
        runCurrent()

        conectado = false
        advanceTimeBy(INTERVALO_MENSAJERIA_MS)
        runCurrent()
        assertEquals(4, contador.total.value)
    }

    @Test fun consultaElTotalSinDescargarConversaciones() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"no_leidos":12}"""))
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MensajeriaApi::class.java)

            assertEquals(12, api.contarNoLeidos().total)
            assertEquals("/api/mensajeria/conversaciones/no-leidos/", server.takeRequest().path)
        }
    }
}
