package com.petly_app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.data.remote.PerfilApi
import com.petly_app.core.network.ApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class FlujoComposeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cuestionarioRealDesdeCompose() {
        val container = (compose.activity.application as PetlyApplication).container
        val email = "compose-${java.util.UUID.randomUUID()}@example.test"
        val password = java.util.UUID.randomUUID().toString()
        runBlocking {
            retryQuota { container.authRepository.register(email,password) }
            retryQuota { container.authRepository.login(email,password) }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Mi cuestionario").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mi cuestionario").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Guardar borrador").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Tipo de vivienda").performTextInput("Departamento")
        compose.onNodeWithText("Horas fuera de casa al día").performTextInput("8.50")
        compose.onNodeWithText("Presupuesto mensual (S/)").performTextInput("150.25")
        compose.onNodeWithContentDescription("¿Viven niños en tu hogar?: No").performScrollTo().performClick()
        compose.onNodeWithContentDescription("¿Tienes otras mascotas?: Sí").performScrollTo().performClick()
        compose.onNodeWithText("Finalizar cuestionario").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Cuestionario guardado.").fetchSemanticsNodes().isNotEmpty() }
        val api = ApiClient.authenticatedApi(container.session).create(PerfilApi::class.java)
        val profile = runBlocking { api.load() }
        assertEquals(false,profile.tieneNinos)
        assertEquals(true,profile.tieneOtrasMascotas)
        assertTrue(profile.finalizado)
    }

    @Test fun enlaceAbreRecuperacionYRechazaTokenInvalido() {
        val activity = compose.activity
        val originalIntent = Intent(activity.intent)
        try {
            compose.runOnUiThread {
                activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("petly://recuperacion?token=enlace-de-prueba-invalido"), activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Nueva contraseña").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nueva contraseña").performTextInput("password1234")
            compose.onNodeWithText("Repetir contraseña").performTextInput("password1234")
            compose.onNodeWithText("Cambiar contraseña").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Revisa los datos ingresados.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Revisa los datos ingresados.").assertIsDisplayed()
            compose.onNodeWithText("Volver al acceso").performScrollTo().performClick()
        } finally {
            // ActivityScenario compara el intent: restaurar incluso si falla una aserción.
            compose.runOnUiThread { activity.intent = originalIntent }
        }
    }

    private suspend fun retryQuota(operation: suspend () -> Unit) {
        repeat(3) { attempt ->
            try { operation(); return }
            catch (e: retrofit2.HttpException) {
                if (e.code() != 429 || attempt == 2) throw e
                kotlinx.coroutines.delay((e.response()?.headers()?.get("Retry-After")?.toLongOrNull() ?: 60L) * 1000 + 1000)
            }
        }
    }

    @Test fun recuperacionRealDesdeMailpitAbreAndroidYCambiaPassword() {
        // Ventana nueva para que la cuota no oculte la recuperación real.
        Thread.sleep(61000)
        val originalIntent = Intent(compose.activity.intent)
        val container = (compose.activity.application as PetlyApplication).container
        val email = "recovery-android-${java.util.UUID.randomUUID()}@example.test"
        val oldPassword = java.util.UUID.randomUUID().toString()
        val newPassword = java.util.UUID.randomUUID().toString()
        runBlocking { container.authRepository.register(email, oldPassword); container.authRepository.login(email, oldPassword) }
        val oldToken = container.session.token()!!
        runBlocking { container.recuperacion.request(email) }
        var link: String? = null
        repeat(20) {
            if (link == null) {
                val messages = org.json.JSONObject(java.net.URL("http://10.0.2.2:8025/api/v1/messages?limit=100").readText()).getJSONArray("messages")
                for (n in 0 until messages.length()) {
                    val message = messages.getJSONObject(n)
                    val to = message.getJSONArray("To")
                    if ((0 until to.length()).any { to.getJSONObject(it).getString("Address") == email }) {
                        val text = org.json.JSONObject(java.net.URL("http://10.0.2.2:8025/api/v1/message/" + message.getString("ID")).readText()).getString("Text")
                        link = Regex("petly://recuperacion\\?token=[A-Za-z0-9_.-]+").find(text)?.value
                        break
                    }
                }
                if (link == null) Thread.sleep(500)
            }
        }
        assertNotNull("Correo Android recibido en Mailpit", link)
        val resetToken = Uri.parse(link!!).getQueryParameter("token")!!
        try {
            compose.runOnUiThread {
                compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link), compose.activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.waitUntil(15000) { compose.onAllNodesWithText("Nueva contraseña").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nueva contraseña").performTextInput(newPassword)
            compose.onNodeWithText("Repetir contraseña").performTextInput(newPassword)
            compose.onNodeWithText("Cambiar contraseña").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Contraseña actualizada. Inicia sesión nuevamente.").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                try { container.recuperacion.confirm(resetToken, newPassword, newPassword); fail("El enlace no debe poder reutilizarse") }
                catch (e: retrofit2.HttpException) { assertEquals(400, e.code()) }
            }
            val connection = java.net.URL("http://10.0.2.2:8080/api/auth/me").openConnection() as java.net.HttpURLConnection
            try { connection.setRequestProperty("Authorization", "Bearer " + oldToken); assertEquals(401, connection.responseCode) }
            finally { connection.disconnect() }
            compose.onNodeWithText("Volver al acceso").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Ingresar", substring = false).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Correo", substring = false).performTextInput(email)
            compose.onNodeWithText("Contraseña", substring = false).performTextInput(newPassword)
            compose.onNodeWithText("Ingresar", substring = false).performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Mi cuestionario").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(container.session.authenticated.value)
        } finally {
            compose.runOnUiThread { compose.activity.intent = originalIntent }
        }
    }

}
