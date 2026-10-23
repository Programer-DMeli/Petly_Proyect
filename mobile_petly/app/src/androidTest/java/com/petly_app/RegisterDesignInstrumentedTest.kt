package com.petly_app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.ui.auth.AuthUiState
import com.petly_app.ui.auth.RegisterScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterDesignInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun fill(password: String = "secreto123", confirmation: String = password) {
        compose.onNodeWithTag("register_name").performScrollTo().performTextInput("Camila Morales")
        compose.onNodeWithTag("register_email").performScrollTo().performTextInput("camila@adopta.org")
        compose.onNodeWithTag("register_password").performScrollTo().performTextInput(password)
        compose.onNodeWithTag("register_confirmation").performScrollTo().performTextInput(confirmation)
    }

    @Test fun requiresConsentAndSubmitsAllValues() {
        var result: List<Any>? = null
        compose.setContent { RegisterScreen(AuthUiState(), { name, email, password, confirmation, accepted ->
            result = listOf(name, email, password, confirmation, accepted)
        }, {}) }
        compose.onNodeWithTag("register_submit").assertIsNotEnabled()
        fill()
        compose.onNodeWithTag("register_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("register_terms").performScrollTo().performClick()
        compose.onNodeWithTag("register_submit").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("Camila Morales", "camila@adopta.org", "secreto123", "secreto123", true), result) }
    }

    @Test fun mismatchedAndShortPasswordsNeverSubmit() {
        var submissions = 0
        compose.setContent { RegisterScreen(AuthUiState(), { _, _, _, _, _ -> submissions++ }, {}) }
        fill(password = "corta", confirmation = "diferente")
        compose.onNodeWithTag("register_terms").performScrollTo().performClick()
        compose.onNodeWithTag("register_submit").performScrollTo().performClick()
        compose.onNodeWithText("Las contraseñas no coinciden.").assertExists()
        compose.onNodeWithText("Usa al menos 8 caracteres", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, submissions) }
    }

    @Test fun passwordsHaveIndependentVisibility() {
        compose.setContent { RegisterScreen(AuthUiState(), { _, _, _, _, _ -> }, {}) }
        fill()
        compose.onNodeWithContentDescription("Mostrar Crea tu contraseña").performScrollTo().performClick()
        compose.onNodeWithTag("register_password").assertTextContains("secreto123")
        compose.onNodeWithContentDescription("Mostrar Confirmar contraseña").assertExists()
        compose.onNodeWithContentDescription("Mostrar Confirmar contraseña").performScrollTo().performClick()
        compose.onNodeWithTag("register_confirmation").assertTextContains("secreto123")
    }

    @Test fun rateLimitAndBusyDisableFormAndNavigation() {
        val state = mutableStateOf(AuthUiState())
        compose.setContent { RegisterScreen(state.value, { _, _, _, _, _ -> }, {}) }
        fill()
        compose.onNodeWithTag("register_terms").performScrollTo().performClick()
        compose.runOnIdle { state.value = AuthUiState(rateLimitRemainingSeconds = 30) }
        compose.onNodeWithTag("register_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Vuelve a intentar en 30 segundos", substring = true).assertExists()
        compose.runOnIdle { state.value = AuthUiState(busy = true) }
        compose.onNodeWithTag("register_name").assertIsNotEnabled()
        compose.onNodeWithTag("register_terms").assertIsNotEnabled()
        compose.onNodeWithText("Inicia sesión").assertIsNotEnabled()
    }

    @Test fun successReplacesFormAndAllowsReturningToLogin() {
        val state = mutableStateOf(AuthUiState(error = "Este correo ya está registrado."))
        var back = 0
        compose.setContent { RegisterScreen(state.value, { _, _, _, _, _ -> }, { back++ }) }
        compose.onNodeWithText("Este correo ya está registrado.").assertExists()
        compose.runOnIdle { state.value = AuthUiState(registered = true) }
        compose.onNodeWithText("¡Cuenta creada con éxito!").assertExists()
        compose.onNodeWithTag("register_password").assertDoesNotExist()
        compose.onAllNodesWithText("Iniciar sesión").onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun policyCanBeReadWithoutCheckingConsent() {
        compose.setContent { RegisterScreen(AuthUiState(), { _, _, _, _, _ -> }, {}) }
        compose.onNodeWithTag("register_policy").performScrollTo()
        compose.onNodeWithText("Términos de Adopción Responsable", substring = true)
            .onChildren().filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Adopción responsable y bienestar animal").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithTag("register_terms").assertIsOff()
    }
}
