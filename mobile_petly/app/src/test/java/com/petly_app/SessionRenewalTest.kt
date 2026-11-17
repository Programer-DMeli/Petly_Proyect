package com.petly_app

import com.petly_app.core.session.RefreshCredential
import com.petly_app.core.session.RefreshTokenStore
import com.petly_app.core.session.SessionExpired
import com.petly_app.core.session.SessionManager
import com.petly_app.core.session.SessionRestore
import com.petly_app.core.session.SessionUnavailable
import com.petly_app.data.model.LoginResponse
import com.petly_app.data.model.UserDto
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SessionRenewalTest {
    private class MemoryStore : RefreshTokenStore {
        var saved: RefreshCredential? = null
        override fun read() = saved
        override fun write(credential: RefreshCredential?) { saved = credential }
    }

    private fun renewed() = LoginResponse(
        token = "access-new", expiresIn = 3600, refreshToken = "refresh-new",
        user = UserDto(id = 17, rol = "ADOPTANTE")
    )

    @Test fun renuevaAntesDeVencerYComparteEntrePeticiones() {
        val store = MemoryStore()
        var time = 0L
        val requests = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val session = SessionManager(store, renew = {
            requests.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            renewed()
        }, now = { time })
        session.start("original", 3600, 17, "refresh-old")
        time = 3_550_000
        val pool = Executors.newFixedThreadPool(4)
        try {
            val responses = (1..4).map { pool.submit<String?> { session.accessForRequest()?.token } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(List(4) { "access-new" }, responses.map { it.get(5, TimeUnit.SECONDS) })
            assertEquals(1, requests.get())
            assertEquals("refresh-new", store.saved?.token)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun conservaSesionVencidaYOperacionAnteFalloTemporal() = runBlocking {
        var time = 0L
        var available = false
        var attempt: String? = null
        val store = MemoryStore()
        val session = SessionManager(store, renew = {
            if (attempt == null) attempt = it.requestId else assertEquals(attempt, it.requestId)
            if (!available) throw IOException()
            renewed()
        }, now = { time })
        session.start("original", 3600, 17, "refresh-old")
        time = 3_700_000
        assertThrows(SessionUnavailable::class.java) { session.accessForRequest() }
        assertTrue(session.authenticated.value)
        assertEquals("refresh-old", store.saved?.token)
        assertNotNull(store.saved?.requestId)

        time += 2_001
        available = true
        assertEquals("access-new", session.accessForRequest()?.token)
        assertNull(store.saved?.requestId)
    }

    @Test fun recuperaDespuesDeRecrearElProcesoYRevocaAlSalir() = runBlocking {
        val store = MemoryStore()
        SessionManager(store).start("original", 3600, 17, "refresh-old")
        var revoked: String? = null
        val restored = SessionManager(store, renew = {
            assertEquals("refresh-old", it.token)
            renewed()
        }, revoke = { revoked = it.token })
        assertFalse(restored.authenticated.value)
        restored.restore()
        assertEquals(SessionRestore.READY, restored.restoreState.value)
        assertEquals("access-new", restored.token())

        restored.logout()
        assertEquals("refresh-new", revoked)
        assertNull(store.saved)
        assertFalse(restored.authenticated.value)
    }

    @Test fun soloElRechazoDefinitivoPideVolverAIniciarSesion() {
        val store = MemoryStore()
        val session = SessionManager(store, renew = {
            throw HttpException(Response.error<LoginResponse>(401, "{}".toResponseBody()))
        })
        session.start("original", 1, 17, "refresh-old")
        assertThrows(SessionExpired::class.java) { session.accessForRequest() }
        assertFalse(session.authenticated.value)
        assertNull(store.saved)
        assertTrue(session.notice.value.orEmpty().contains("Inicia sesión"))
    }
}
