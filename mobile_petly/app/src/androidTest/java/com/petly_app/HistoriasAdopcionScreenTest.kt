package com.petly_app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.petly_app.data.remote.AdopcionElegibleDto
import com.petly_app.data.remote.HistoriaAdopcionDto
import com.petly_app.data.remote.HistoriasAdopcionApi
import com.petly_app.data.remote.HistoriasAdopcionPagina
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.data.repository.HistoriasAdopcionRepository
import com.petly_app.ui.perfil.MisSolicitudesAdopcion
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class HistoriasAdopcionScreenTest {
    @get:Rule val ui = createComposeRule()

    @Test fun muestraLaElegibilidadYEnviaUnaHistoriaConFotoParaRevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contenidoFoto = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=",
            android.util.Base64.DEFAULT,
        )
        val archivo = File.createTempFile("historia", ".png", context.cacheDir)
        archivo.writeBytes(contenidoFoto)
        val api = HistoriasDePrueba()
        val repository = HistoriasAdopcionRepository(api, context.contentResolver)
        val selector = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    assertArrayEquals(arrayOf("image/jpeg", "image/png"), input as Array<*>)
                    dispatchResult(requestCode, listOf(Uri.fromFile(archivo)))
                }
            }
        }

        try {
            ui.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides selector) {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            MisSolicitudesAdopcion(
                                listarSolicitudes = {
                                    listOf(
                                        SolicitudPersistida(41, 1, "ENTREGADA", "Nube", null),
                                        SolicitudPersistida(42, 2, "ENTREGADA", "Luna", null),
                                        SolicitudPersistida(43, 3, "ENTREGADA", "Sol", null),
                                        SolicitudPersistida(44, 4, "APROBADA", "Mar", null),
                                    )
                                },
                                historiasRepository = repository,
                            )
                        }
                    }
                }
            }

            ui.onAllNodesWithText("Compartir mi historia").assertCountEquals(1)
            ui.onNodeWithText("Motivo del rechazo: La foto muestra datos personales.")
                .performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Compartir mi historia").performScrollTo().performClick()
            ui.onNodeWithText("Enviar para revisión").assertIsNotEnabled()
            ui.onNodeWithText("Tu historia").performTextInput("Nube se adaptó a su nuevo hogar.")
            ui.onNodeWithText("Seleccionar fotos").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Foto seleccionada 1").assertExists()
            ui.onNodeWithText("Enviar para revisión").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodesWithText("Historia de adopción · Pendiente de revisión").fetchSemanticsNodes().size == 1
            }
            ui.onAllNodesWithText("Compartir mi historia").assertCountEquals(0)
            ui.runOnIdle {
                assertEquals("41", api.solicitudEnviada)
                assertEquals("Nube se adaptó a su nuevo hogar.", api.textoEnviado)
                assertArrayEquals(contenidoFoto, api.fotoEnviada)
            }
        } finally {
            archivo.delete()
        }
    }

    private class HistoriasDePrueba : HistoriasAdopcionApi {
        var solicitudEnviada: String? = null
        var textoEnviado: String? = null
        var fotoEnviada: ByteArray? = null

        override suspend fun adopcionesDisponibles() = listOf(AdopcionElegibleDto(41))

        override suspend fun listar(page: Int) = HistoriasAdopcionPagina(
            listOf(HistoriaAdopcionDto(42, "RECHAZADO", "Rechazado", "La foto muestra datos personales.")),
            next = null,
        )

        override suspend fun enviar(
            solicitudId: RequestBody,
            historia: RequestBody,
            fotos: List<MultipartBody.Part>,
        ): HistoriaAdopcionDto {
            solicitudEnviada = Buffer().apply { solicitudId.writeTo(this) }.readUtf8()
            textoEnviado = Buffer().apply { historia.writeTo(this) }.readUtf8()
            val foto = fotos.single()
            assertEquals(true, foto.headers?.get("Content-Disposition")?.contains("name=\"fotos\""))
            fotoEnviada = Buffer().apply { foto.body.writeTo(this) }.readByteArray()
            return HistoriaAdopcionDto(41, "PENDIENTE", "Pendiente de revisión", null)
        }
    }
}
