package com.petly_app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.core.network.ApiClient
import com.petly_app.data.model.LoginRequest
import com.petly_app.data.remote.AuthApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AccesoBloqueoInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun aInvalidFormatAndTwoWrongPasswordsShowCountdown() {
        // React/emulador pueden compartir IP; esperar la ventana real sin eludir la cuota.
        Thread.sleep(61000)
        val container = (compose.activity.application as PetlyApplication).container
        container.session.clear()
        val email = "lock-android-${java.util.UUID.randomUUID()}@example.test"
        val password = java.util.UUID.randomUUID().toString()
        runBlocking { container.authRepository.register(email, password) }
        compose.onNodeWithTag("login_email").performTextInput("nombre@gmail")
        compose.onNodeWithTag("login_password").performTextInput("x")
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Este error de formato no cuenta", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("login_email").performTextClearance()
        compose.onNodeWithTag("login_email").performTextInput(email)
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Credenciales inválidas o sesión vencida.").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("login_submit").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Puedes volver a intentar en", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_password").performTextClearance()
        compose.onNodeWithTag("login_password").performTextInput(password)
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        assertFalse(container.session.authenticated.value)
    }

    @Test fun bRateLimitFromServerDisablesLoginWithCountdown() {
        // Cuota real compartida: esperar una ventana limpia, no modificar el servidor.
        Thread.sleep(61000)
        val email = "missing-rate-${java.util.UUID.randomUUID()}@example.test"
        val api = ApiClient.publicApi().create(AuthApi::class.java)
        var limited = false
        runBlocking {
            repeat(11) {
                if (!limited) {
                    try { api.login(LoginRequest(email, "x")); fail("No debe acceder una cuenta inexistente") }
                    catch (e: HttpException) {
                        if (e.code() == 429) limited = true else assertEquals(401, e.code())
                    }
                }
            }
        }
        assertTrue("La cuota debe aplicarse en Spring", limited)
        compose.onNodeWithTag("login_email").performTextInput(email)
        compose.onNodeWithTag("login_password").performTextInput("x")
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Límite de peticiones alcanzado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Puedes volver a intentar en", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
