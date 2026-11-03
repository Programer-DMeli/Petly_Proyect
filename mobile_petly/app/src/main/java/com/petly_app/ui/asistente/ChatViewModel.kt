package com.petly_app.ui.asistente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.data.remote.ChatMessage
import com.petly_app.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ChatUiState(
    val open: Boolean = false,
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val repository: ChatRepository,
    authenticated: StateFlow<Boolean>? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    init {
        if (authenticated != null) viewModelScope.launch {
            authenticated.collect { active -> if (!active) { clear(); open(false) } }
        }
    }

    fun open(value: Boolean) { mutableState.update { it.copy(open = value) } }
    fun edit(text: String) {
        if (text.length <= 2000) mutableState.update { it.copy(draft = text) }
    }

    fun send() {
        val current = state.value
        if (current.sending || current.error != null || current.draft.isBlank()) return
        mutableState.update {
            it.copy(messages = it.messages + ChatMessage("user", it.draft.trim()), draft = "")
        }
        respond()
    }

    fun retry() {
        if (!state.value.sending && state.value.error != null && state.value.messages.lastOrNull()?.role == "user") respond()
    }

    fun editFailed() {
        if (state.value.sending || state.value.error == null) return
        mutableState.update { it.copy(draft = it.messages.last().text, messages = it.messages.dropLast(1), error = null) }
    }

    fun clear() {
        generation++
        request?.cancel()
        mutableState.update { ChatUiState(open = it.open) }
    }

    private fun errorMessage(ex: Exception): String = when {
        ex is HttpException && ex.code() == 401 -> "Tu sesión terminó. Vuelve a iniciar sesión para conversar."
        ex is HttpException && ex.code() == 429 -> "Has enviado varias consultas seguidas. Espera un minuto y reintenta."
        ex is HttpException && ex.code() == 503 -> "El asistente no está disponible en este momento. Puedes reintentar en unos instantes."
        ex is IOException -> "No pudimos conectar. Revisa tu conexión y vuelve a intentar."
        else -> "No pudimos completar la respuesta. Tu mensaje se conserva para reintentar."
    }

    private fun respond() {
        val epoch = generation
        // Retain complete user/model pairs, with the latest user turn last.
        var context = state.value.messages.takeLast(21)
        while (context.sumOf { it.text.length } > 16_000 && context.size > 1) context = context.drop(2)
        mutableState.update { it.copy(sending = true, error = null) }
        request = viewModelScope.launch {
            try {
                val reply = repository.reply(context)
                if (epoch != generation) return@launch
                mutableState.update {
                    it.copy(messages = (it.messages + ChatMessage("model", reply)).takeLast(100), sending = false)
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                if (epoch == generation) mutableState.update { it.copy(sending = false, error = errorMessage(ex)) }
            }
        }
    }
}
