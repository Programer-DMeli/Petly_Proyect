package com.petly_app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.*
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real UI, HTTP and persistence; creates only an isolated adopter account. */
class CatalogoRealJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun fotosYPorcentajesSeActualizanTrasGuardarDesdeElFormulario() = runBlocking {
        val session = SessionManager()
        val auth = AuthRepository(ApiClient.publicApi().create(AuthApi::class.java), session)
        val email = "catalog-journey-${java.util.UUID.randomUUID()}@example.test"
        val password = java.util.UUID.randomUUID().toString()
        auth.register(email, password, "Prueba catálogo", true)
        auth.login(email, password)
        val remote = ApiClient.authenticatedApi(session)
        val catalog = RemoteCatalogoRepository(remote.create(CatalogoApi::class.java))
        val profiles = RemotePerfilRepository(remote.create(PerfilApi::class.java))
        profiles.save(PerfilDraft("Casa", "3", "300", false, false, true))
        val pet = catalog.search(CatalogoFilters(search = "Sprint2 Match"), 1, 100).items
            .first { it.tamano == "Grande" && it.energia == "Alta" && it.temperamento == "Sociable" }
        assertEquals(100, pet.compatibilidad?.porcentaje)
        val photoUrl = requireNotNull(pet.fotografiaUrl)
        assertFalse("La foto no puede apuntar al loopback del emulador", photoUrl.contains("127.0.0.1"))
        val bytes = OkHttpClient().newCall(Request.Builder().url(photoUrl).build()).execute().use {
            assertTrue(it.isSuccessful); requireNotNull(it.body).bytes()
        }
        assertNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val container = (ui.activity.application as PetlyApplication).container
        container.authRepository.login(email, password)
        ui.waitUntil(20000) { ui.onAllNodesWithText("Catálogo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Catálogo", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Buscar...").performTextInput(pet.nombre)
        ui.waitUntil(20000) { ui.onAllNodesWithText(pet.nombre).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("100% compatible").assertIsDisplayed()
        waitForPhoto()
        screenshot("catalogo-antes.png")
        ui.onNodeWithText("Perfil", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("Mi cuestionario").performScrollTo().performClick()
        ui.onNodeWithText("Departamento").performScrollTo().performClick()
        ui.onNodeWithText("Horas fuera de casa (0 a 24)").performScrollTo().performTextReplacement("10")
        ui.activityRule.scenario.onActivity { activity ->
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        ui.onNodeWithContentDescription("Finalizar cuestionario").performScrollTo().performClick()
        ui.waitUntil(20000) { ui.onAllNodesWithText("Cuestionario guardado.").fetchSemanticsNodes().isNotEmpty() }
        val saved = profiles.load()!!
        assertEquals("Departamento", saved.vivienda)
        assertEquals(10.0, saved.horasFuera.toDouble(), 0.001)
        assertTrue(saved.finalizado)
        val updated = catalog.detail(pet.id)!!
        val score = requireNotNull(updated.compatibilidad?.porcentaje)
        assertTrue(score < 100)
        ui.onNodeWithText("Catálogo", useUnmergedTree = true).performClick()
        ui.waitUntil(20000) { ui.onAllNodesWithText("$score% compatible").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("$score% compatible").assertIsDisplayed()
        waitForPhoto()
        screenshot("catalogo-despues.png")
        assertEquals(photoUrl, updated.fotografiaUrl)
        val report = JSONObject().put("correo", email).put("password", password)
            .put("mascotaId", pet.id).put("nombre", pet.nombre).put("fotoUrlAndroid", photoUrl)
            .put("fotoSha256", hash).put("antes", 100).put("despues", score)
        File(ui.activity.filesDir, "catalogo-recorrido-privado.json").writeText(report.toString())
        session.clear()
    }

    private fun waitForPhoto() {
        ui.waitUntil(20000) { ui.onAllNodesWithText("Cargando fotografía…").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("No se pudo cargar la fotografía").assertDoesNotExist()
        ui.onNodeWithText("Fotografía no registrada").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(ui.activity.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
