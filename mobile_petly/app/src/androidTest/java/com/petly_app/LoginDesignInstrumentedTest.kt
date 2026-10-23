package com.petly_app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.ui.auth.AuthUiState
import com.petly_app.ui.auth.LoginScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the access UI in isolation: no backend, real accounts or session changes. */
@RunWith(AndroidJUnit4::class)
class LoginDesignInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun credentialsVisibilityAndSubmit() {
        var submitted: Pair<String, String>? = null
        compose.setContent { LoginScreen(AuthUiState(), { email, password -> submitted = email to password }, {}, {}) }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_email").performTextInput("persona@example.test")
        compose.onNodeWithTag("login_password").performTextInput("secreto123")
        compose.onNodeWithContentDescription("Mostrar contraseña").performScrollTo().performClick()
        compose.onNodeWithTag("login_password").assertTextContains("secreto123")
        compose.onNodeWithContentDescription("Ocultar contraseña").performClick()
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("persona@example.test" to "secreto123", submitted) }
    }

    @Test fun cooldownAndBusyStatePreventSubmission() {
        val state = mutableStateOf(AuthUiState(blockedEmail = "persona@example.test", lockRemainingSeconds = 60))
        var submissions = 0
        compose.setContent { LoginScreen(state.value, { _, _ -> submissions++ }, {}, {}) }
        compose.onNodeWithTag("login_email").performTextInput("persona@example.test")
        compose.onNodeWithTag("login_password").performTextInput("secreto123")
        compose.onNodeWithTag("login_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Puedes volver a intentar en 1:00", substring = true).assertExists()
        compose.runOnIdle { state.value = AuthUiState(busy = true) }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_email").assertIsNotEnabled()
        compose.runOnIdle { state.value = AuthUiState() }
        compose.onNodeWithTag("login_submit").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, submissions) }
    }

    @Test fun recoveryAndBothRegistrationLinksNavigate() {
        var registrations = 0
        var recoveries = 0
        compose.setContent { LoginScreen(AuthUiState(), { _, _ -> }, { registrations++ }, {}, onRecovery = { recoveries++ }) }
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Regístrate gratis").performScrollTo().performClick()
        compose.onNodeWithText("¿Olvidaste tu contraseña?").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, registrations); assertEquals(1, recoveries) }
    }

    @Test fun alternativeAccessExplainsAvailabilityWithoutLoggingIn() {
        var submissions = 0
        compose.setContent { LoginScreen(AuthUiState(), { _, _ -> submissions++ }, {}, {}) }
        for (provider in listOf("Google", "Huella")) {
            compose.onNodeWithText(provider).performScrollTo().performClick()
            compose.onNodeWithText("Próximamente").assertIsDisplayed()
            compose.onNodeWithText("Entendido").performClick()
        }
        compose.runOnIdle { assertEquals(0, submissions) }
    }
}
