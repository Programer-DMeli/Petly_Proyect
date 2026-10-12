package com.petly_app
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
import com.petly_app.data.model.*
@RunWith(AndroidJUnit4::class)
class SprintIntegrationInstrumentedTest {
    @Test fun realApisFromEmulator() = runBlocking {
        val public = ApiClient.publicApi()
        val health = public.create(HealthApi::class.java)
        assertTrue(health.check().isSuccessful)
        val session = SessionManager()
        val auth = AuthRepository(public.create(AuthApi::class.java),session)
        val email = "android-${java.util.UUID.randomUUID()}@example.test"
        val password = java.util.UUID.randomUUID().toString()
        auth.register(email,password); auth.login(email,password)
        assertTrue(session.authenticated.value)
        val private = ApiClient.authenticatedApi(session)
        val profile = RemotePerfilRepository(private.create(PerfilApi::class.java))
        val draft = PerfilDraft("Departamento","8.50","150.25",false,null,false)
        profile.save(draft)
        assertEquals(false,profile.load()?.tieneNinos)
        assertNull(profile.load()?.tieneOtrasMascotas)
        profile.save(draft.copy(tieneOtrasMascotas = true,finalizado = true))
        assertTrue(profile.load()?.finalizado == true)
        val catalog = RemoteCatalogoRepository(private.create(CatalogoApi::class.java))
        assertTrue(catalog.options().temperamentos.contains("Por evaluar"))
        val page = catalog.search(CatalogoFilters(),1,2)
        assertTrue("Preparar datos Django antes de esta prueba",page.items.isNotEmpty())
        assertEquals(page.items.first().id,catalog.detail(page.items.first().id)?.id)
        val consent = AutorizacionRepository(private.create(AutorizacionApi::class.java))
        val shelter = consent.albergues().first()
        consent.allow(shelter.id); assertTrue(consent.list().any { it.albergueId == shelter.id })
        consent.revoke(shelter.id); assertFalse(consent.list().any { it.albergueId == shelter.id })
        session.clear();assertFalse(session.authenticated.value)
    }
    @Test fun suppliedPhotosAreVisibleAndDecodeOnAndroid() = runBlocking {
        val session = SessionManager()
        val auth = AuthRepository(ApiClient.publicApi().create(AuthApi::class.java), session)
        val password = java.util.UUID.randomUUID().toString()
        val email = "photos-${java.util.UUID.randomUUID()}@example.test"
        auth.register(email, password); auth.login(email, password)
        val catalog = RemoteCatalogoRepository(ApiClient.authenticatedApi(session).create(CatalogoApi::class.java))
        for (name in listOf("Luna", "Toby", "Bruno", "Miel", "Copito", "Simba", "Nube")) {
            val pet = catalog.search(CatalogoFilters(search = name), 1, 100).items.first {
                it.nombre == name && it.descripcion.contains("Publicación de demostración de Petly")
            }
            assertNull(pet.edadMeses)
            assertEquals("Por evaluar", pet.temperamento)
            val connection = java.net.URI(pet.fotografiaUrl!!).toURL().openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            try {
                assertEquals(200, connection.responseCode)
                val bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                assertNotNull("Fotografía real de $name", bitmap)
                assertTrue(bitmap.width > 100 && bitmap.height > 100)
                bitmap.recycle()
            } finally { connection.disconnect() }
        }
        session.clear()
    }
}
