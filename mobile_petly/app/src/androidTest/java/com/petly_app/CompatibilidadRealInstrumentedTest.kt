package com.petly_app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
import com.petly_app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real local Spring/Django/MariaDB. Gemini response may be available or handled as optional. */
@RunWith(AndroidJUnit4::class)
class CompatibilidadRealInstrumentedTest {
    @Test fun perfilPersistidoRecalculaCatalogoYConsejosNoAlteranPuntaje() = runBlocking {
        val session = SessionManager()
        try {
            val auth = AuthRepository(ApiClient.publicApi().create(AuthApi::class.java), session)
            val email = "android-match-${java.util.UUID.randomUUID()}@example.test"
            val password = java.util.UUID.randomUUID().toString()
            auth.register(email, password); auth.login(email, password)
            val remote = ApiClient.authenticatedApi(session)
            val catalog = RemoteCatalogoRepository(remote.create(CatalogoApi::class.java))
            val profiles = RemotePerfilRepository(remote.create(PerfilApi::class.java))
            val profile = PerfilDraft("Casa", "3", "300", false, false, true)
            profiles.save(profile)
            assertEquals("Casa", profiles.load()?.vivienda)
            val list = catalog.search(CatalogoFilters(search = "Sprint2 Match"), 1, 100)
            val pet = list.items.first { it.temperamento == "Sociable" && it.energia == "Alta" && it.tamano == "Grande" }
            val first = catalog.detail(pet.id)!!
            assertEquals(100, first.compatibilidad?.porcentaje)
            assertEquals(pet.compatibilidad, first.compatibilidad)
            assertEquals(3, first.compatibilidad?.criterios?.size)
            assertEquals(100.0, first.compatibilidad!!.criterios.sumOf { it.aporte ?: 0.0 }, 0.001)
            try {
                profiles.save(profile.copy(horasFuera = "25"))
                fail("El servidor debe rechazar horas fuera inválidas")
            } catch (expected: retrofit2.HttpException) { assertEquals(400, expected.code()) }
            assertEquals(100, catalog.detail(pet.id)?.compatibilidad?.porcentaje)
            profiles.save(profile.copy(vivienda = "Departamento", horasFuera = "10", tieneNinos = true))
            val changed = catalog.detail(pet.id)!!
            assertTrue(changed.compatibilidad!!.porcentaje!! < 100)
            val refreshed = catalog.search(CatalogoFilters(search = "Sprint2 Match"), 1, 100)
            assertEquals(changed.compatibilidad, refreshed.items.first { it.id == pet.id }.compatibilidad)
            val advice = catalog.consejos(pet.id)
            println("Gemini vía Spring: ${advice.estado}; puntaje por reglas: ${changed.compatibilidad.porcentaje}")
            assertTrue(advice.estado in listOf("DISPONIBLE", "NO_DISPONIBLE", "NO_CONFIGURADO"))
            assertEquals(changed.compatibilidad, catalog.detail(pet.id)?.compatibilidad)
        } finally { session.clear() }
    }
}
