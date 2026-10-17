package com.petly_app

import com.google.gson.JsonParser
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.AuthApi
import com.petly_app.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegistrationContractTest {
    @Test fun registrationSendsFullNameAndExplicitConsent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
            val session = SessionManager()
            AuthRepository(api, session).register(" camila@adopta.org ", "secreto123", " Camila Morales ", true)
            val request = server.takeRequest()
            assertEquals("/api/auth/registro", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("camila@adopta.org", body["correo"].asString)
            assertEquals("Camila Morales", body["firstName"].asString)
            assertTrue(body["aceptoTerminos"].asBoolean)
            assertFalse(session.authenticated.value)
        }
    }

    @Test fun olderCallersDoNotInventConsent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
            AuthRepository(api, SessionManager()).register("test@petly.test", "secreto123")
            val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            assertFalse(body["aceptoTerminos"].asBoolean)
            assertFalse(body.has("firstName"))
        }
    }
}
