package com.petly_app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.LoginRequest
import com.petly_app.data.model.RegisterRequest
import com.petly_app.data.remote.AuthApi
import com.petly_app.data.remote.CriteriosAvisoDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AltaMascotaEnsayoApi {
    @POST("api/mascotas/")
    suspend fun crear(@Body mascota: RequestBody): Response<Unit>
}

class AvisosMascotasIntegracionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>(effectContext = Dispatchers.Main.immediate)

    @Test fun laBandejaAbiertaRecibeUnaMascotaNuevaSinActualizarAMano() = runBlocking<Unit> {
        val fixture = File(ui.activity.filesDir, "petly-us44-fixtures.json")
        assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".integration") &&
            BuildConfig.API_BASE_URL == "http://10.0.2.2:8082/" &&
            BuildConfig.ADMIN_API_BASE_URL == "http://10.0.2.2:8002/" && fixture.exists())
        val albergue = JSONObject(fixture.readText()).getJSONObject("ALBERGUE")
        val auth = ApiClient.publicApi().create(AuthApi::class.java)
        val id = UUID.randomUUID().toString().take(12)
        val correo = "us44-android-$id@example.test"
        val password = UUID.randomUUID().toString()
        assertEquals(201, auth.register(RegisterRequest(correo, password)).code())
        val login = auth.login(LoginRequest(albergue.getString("correo"), albergue.getString("password")))
        val session = SessionManager().apply {
            start(requireNotNull(login.token), login.getEffectiveExpiresIn(), requireNotNull(login.user?.id))
        }
        val albergueApi = ApiClient.authenticatedAdminApi(session).create(AltaMascotaEnsayoApi::class.java)
        val container = (ui.activity.application as PetlyApplication).container
        container.session.logout()
        try {
            val adoptante = auth.login(LoginRequest(correo, password))
            ui.runOnIdle {
                container.session.start(requireNotNull(adoptante.token), adoptante.getEffectiveExpiresIn(),
                    requireNotNull(adoptante.user?.id), adoptante.refreshToken)
            }
            esperarTexto("Novedades")
            assertTrue(container.notificacionesApi.guardarCriterios(
                CriteriosAvisoDto(activo = true, especie = "PERRO")
            ).activo)
            ui.onNodeWithText("Novedades", useUnmergedTree = true).performClick()
            ui.onNodeWithText("Mis notificaciones").performClick()
            esperarTexto("0 sin leer")

            val nombre = "Mascota US44 $id"
            val imagen = ByteArrayOutputStream().apply {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                bitmap.recycle()
            }
            val alta = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("nombre", nombre)
                .addFormDataPart("especie", "PERRO")
                .addFormDataPart("sexo", "HEMBRA")
                .addFormDataPart("foto_principal", "petly.png",
                    imagen.toByteArray().toRequestBody("image/png".toMediaType()))
                .build()
            assertEquals(201, albergueApi.crear(alta).code())
            esperarTexto("1 sin leer")
            esperarTexto(nombre, substring = true)
            ui.onNodeWithText("Marcar como leída").performClick()
            esperarTexto("0 sin leer")
        } finally {
            container.session.logout()
        }
    }

    private fun esperarTexto(texto: String, substring: Boolean = false) {
        ui.waitUntil(22_000) {
            ui.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
