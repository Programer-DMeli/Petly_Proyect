package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class ChatMessage(val role: String, val text: String)
data class ChatRequest(val messages: List<ChatMessage>)
data class ChatResponse(val reply: String)

interface ChatApi {
    @POST("api/asistente/chat")
    suspend fun send(@Body request: ChatRequest): ChatResponse
}
