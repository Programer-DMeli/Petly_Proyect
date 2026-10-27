package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.data.model.*
import com.petly_app.ui.catalogo.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI verification with a simulated IA response; no real Gemini call in this test. */
@RunWith(AndroidJUnit4::class)
class CompatibilidadComposeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun muestraDesgloseYPermiteReintentarIASinCambiarPuntaje() {
        val criteria = listOf(
            CriterioCompatibilidad("vivienda", "Vivienda", 30, 100, 30.0, "Espacio registrado"),
            CriterioCompatibilidad("rutina", "Rutina", 35, 100, 35.0, "Rutina registrada"),
            CriterioCompatibilidad("convivencia", "Convivencia", 35, 100, 35.0, "Convivencia registrada"))
        val pet = Mascota(1, "Mascota de prueba", "Perro", "Grande", 24, "Sociable", "Alta", "Pelaje marrón", "Albergue de prueba", "Lima", "Disponible",
            compatibilidad = Compatibilidad(100, 100, 100, false, "Puntaje orientativo", "petly-compat-v1", criteria))
        val state = mutableStateOf(DetalleMascotaUiState(mascota = pet))
        var calls = 0
        compose.setContent {
            MaterialTheme {
                DetalleMascotaScreen(state.value, false, {}, {}, onConsejos = {
                    calls++
                    state.value = state.value.copy(consejos = if (calls == 1) ConsejosIA("NO_DISPONIBLE", "Puedes reintentar") else ConsejosIA("DISPONIBLE", "Consejo complementario", listOf("Coordina paseos")))
                })
            }
        }
        compose.onNodeWithText("100% compatible").performScrollTo().assertIsDisplayed()
        for (name in listOf("Vivienda", "Rutina", "Convivencia")) {
            compose.onNodeWithText("$name: peso", substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Cobertura: 100% · petly-compat-v1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Consultar o reintentar consejos Gemini").performScrollTo().performClick()
        compose.onNodeWithText("Puedes reintentar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Consultar o reintentar consejos Gemini").performScrollTo().performClick()
        compose.onNodeWithText("Coordina paseos", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("100% compatible").performScrollTo().assertIsDisplayed()
    }
}
