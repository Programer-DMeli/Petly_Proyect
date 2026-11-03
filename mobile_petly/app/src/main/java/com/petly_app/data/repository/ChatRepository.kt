package com.petly_app.data.repository

import com.petly_app.data.remote.ChatApi
import com.petly_app.data.remote.ChatMessage
import com.petly_app.data.remote.ChatRequest

fun interface ChatRepository {
    suspend fun reply(messages: List<ChatMessage>): String
}

class RemoteChatRepository(private val api: ChatApi) : ChatRepository {
    override suspend fun reply(messages: List<ChatMessage>): String {
        val reply = api.send(ChatRequest(messages)).reply
        require(reply.isNotBlank() && reply.length <= 2000) { "El asistente no devolvió una respuesta válida." }
        return reply
    }
}
