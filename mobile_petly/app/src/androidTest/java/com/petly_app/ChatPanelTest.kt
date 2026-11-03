package com.petly_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.petly_app.data.remote.ChatMessage
import com.petly_app.ui.asistente.ChatPanel
import com.petly_app.ui.asistente.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permiteEnviarMuestraBurbujasYConservaHistorialAlReabrir() {
        val state = mutableStateOf(ChatUiState(open = true))
        var sent = ""
        compose.setContent {
            MaterialTheme {
                ChatPanel(
                    state.value,
                    onOpen = { state.value = state.value.copy(open = it) },
                    onEdit = { state.value = state.value.copy(draft = it) },
                    onSend = {
                        sent = state.value.draft
                        state.value = state.value.copy(draft = "", messages = listOf(
                            ChatMessage("user", sent), ChatMessage("model", "¿Cómo es tu vivienda?")
                        ))
                    },
                    onRetry = {}, onEditFailed = {}, onClear = { state.value = ChatUiState(open = true) }
                )
            }
        }
        compose.onNodeWithText("Escribe tu consulta…").performTextInput("Quiero adoptar un gato")
        compose.onNodeWithText("Enviar").performClick()
        compose.runOnIdle { assertEquals("Quiero adoptar un gato", sent) }
        compose.onNodeWithText("Quiero adoptar un gato").assertExists()
        compose.onNodeWithText("¿Cómo es tu vivienda?").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Asistente Petly").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(open = true) }
        compose.onNodeWithText("Quiero adoptar un gato").assertExists()
        compose.onNodeWithText("Nueva conversación").performClick()
        compose.onNodeWithText("Quiero adoptar un gato").assertDoesNotExist()
    }
}
