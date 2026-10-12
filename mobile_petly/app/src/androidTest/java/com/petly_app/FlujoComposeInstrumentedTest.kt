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

@RunWith(AndroidJUnit4::class)
class FlujoComposeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cuestionarioRealDesdeCompose() {
        val container = (compose.activity.application as PetlyApplication).container
        val email = "compose-${java.util.UUID.randomUUID()}@example.test"
        val password = java.util.UUID.randomUUID().toString()
        runBlocking { container.authRepository.register(email,password); container.authRepository.login(email,password) }
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
        val originalIntent = Intent(compose.activity.intent)
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("petly://recuperacion?token=enlace-de-prueba-invalido"),compose.activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Nueva contraseña").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nueva contraseña").performTextInput("password1234")
        compose.onNodeWithText("Repetir contraseña").performTextInput("password1234")
        compose.onNodeWithText("Cambiar contraseña").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Revisa los datos ingresados.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revisa los datos ingresados.").assertIsDisplayed()
        compose.onNodeWithText("Volver al acceso").performScrollTo().performClick()
        // ActivityScenario identifica su actividad por el intent original. Restaurar solo el fixture.
        compose.runOnUiThread { compose.activity.intent = originalIntent }
    }
}
