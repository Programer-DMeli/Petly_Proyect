package com.petly_app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petly_app.core.session.KeystoreRefreshTokenStore
import com.petly_app.core.session.SessionManager
import com.petly_app.data.model.LoginResponse
import com.petly_app.data.model.UserDto
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionKeystoreInstrumentedTest {
    @Test fun restauraEnOtroProceso() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("sessionPhase")
        assumeTrue(phase == "save" || phase == "restore")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "petly_session_restart_test"
        val store = KeystoreRefreshTokenStore(context, name)
        val refresh = "refresh-test-de-reinicio"
        val process = android.os.Process.myPid()
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

        if (phase == "save") {
            SessionManager(store).login("access", 3600, 7, refresh)
            check(preferences.edit().putInt("process", process).commit())
        } else {
            try {
                assertNotEquals(process, preferences.getInt("process", process))
                val recovered = SessionManager(store, renew = {
                    assertEquals(refresh, it.token)
                    LoginResponse(token = "new-access", expiresIn = 3600, refreshToken = "new-refresh",
                        user = UserDto(id = 7, rol = "ADOPTANTE"))
                })
                recovered.restore()
                assertEquals("new-access", recovered.token())
            } finally {
                context.deleteSharedPreferences(name)
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("$name.refresh.v1") }
            }
        }
    }

    @Test fun otraInstanciaRestauraConLaClaveAndroidKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "petly_session_test_${UUID.randomUUID()}"
        val refresh = "credencial-opaca-de-prueba"
        try {
            SessionManager(KeystoreRefreshTokenStore(context, name)).login("access", 3600, 7, refresh)
            val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("refresh", null)
            assertNotNull(raw)
            assertFalse(raw.orEmpty().contains(refresh))

            val recovered = SessionManager(KeystoreRefreshTokenStore(context, name), renew = {
                assertEquals(refresh, it.token)
                assertNotNull(it.requestId)
                LoginResponse(token = "new-access", expiresIn = 3600, refreshToken = "new-refresh",
                    user = UserDto(id = 7, rol = "ADOPTANTE"))
            })
            recovered.restore()
            assertTrue(recovered.authenticated.value)
            assertEquals("new-access", recovered.token())
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("$name.refresh.v1") }
        }
    }
}
