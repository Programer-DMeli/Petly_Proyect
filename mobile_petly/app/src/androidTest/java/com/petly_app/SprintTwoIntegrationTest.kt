package com.petly_app

import android.graphics.Bitmap
import android.provider.MediaStore
import android.content.ContentValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petly_app.core.network.ApiClient
import com.petly_app.core.session.SessionManager
import com.petly_app.data.remote.*
import com.petly_app.data.repository.*
import com.petly_app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SprintTwoIntegrationTest {
    @Test fun solicitudFotosFavoritosYHorariosPersistenEnServiciosReales() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val session=SessionManager()
        val auth=AuthRepository(ApiClient.publicApi().create(AuthApi::class.java),session)
        val password=java.util.UUID.randomUUID().toString()
        val email="sprint2-android-login-${java.util.UUID.randomUUID()}@example.test"
        auth.register(email,password);auth.login(email,password)
        val api=ApiClient.authenticatedApi(session)
        val catalogo=RemoteCatalogoRepository(api.create(CatalogoApi::class.java))
        val pet=catalogo.search(CatalogoFilters(search="Integración S2"),1,100).items.first { it.estado=="Disponible" }
        val perfil=RemotePerfilRepository(api.create(PerfilApi::class.java))
        perfil.save(PerfilDraft("Casa","4","300",false,false,true))
        val permisos=AutorizacionRepository(api.create(AutorizacionApi::class.java))
        val shelter=permisos.albergues().first { it.nombre==pet.albergue }
        permisos.allow(shelter.id)
        val favorites=api.create(FavoritosApi::class.java)
        assertTrue(favorites.agregar(pet.id).isSuccessful)
        assertTrue(favorites.agregar(pet.id).isSuccessful)
        assertEquals(1,favorites.listar().count { it.id==pet.id })
        val horarios=api.create(DisponibilidadApi::class.java)
        horarios.save(listOf(FranjaHoraria(null,"Martes","09:00","10:00","Prueba Android")))
        assertEquals("Martes",horarios.list().single().diaSemana)
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"petly-test-${java.util.UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        })!!
        try {
            val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            SolicitudAdopcionManager.clear()
            fun configure() {
                SolicitudAdopcionManager.api=api.create(SolicitudesApi::class.java)
                SolicitudAdopcionManager.fotosApi=ApiClient.authenticatedAdminApi(session).create(FotosHogarApi::class.java)
                SolicitudAdopcionManager.resolver=resolver
            }
            configure()
            SolicitudAdopcionManager.fotosLocales[pet.id]=listOf(uri)
            val draft=SolicitudAdopcionDraft(mascotaId=pet.id,mascotaNombre=pet.nombre,nombreCompleto="Prueba Android",dni="00000000",direccionHogar="Lima",telefonoContacto="999000111",motivoAdopcion="Prueba de persistencia",tieneAlergias=false,vivesConPersonasEspeciales=false,haCuidadoMascotasAntes=true,aceptaEsterilizacion=true,aceptaSeguimiento=true,puedeCubrirGastosVeterinarios=true)
            val errores=SolicitudAdopcionManager.enviarSolicitud(draft)
            assertTrue(errores.toString(),errores.isEmpty())
            SolicitudAdopcionManager.clear();configure();SolicitudAdopcionManager.cargar()
            assertTrue(SolicitudAdopcionManager.isSolicitudEnviada(pet.id))
            assertEquals("Prueba Android",SolicitudAdopcionManager.getDraft(pet.id,pet.nombre).nombreCompleto)
            assertEquals(1,SolicitudAdopcionManager.fotosGuardadas(pet.id).size)
            assertEquals("Disponible",catalogo.detail(pet.id)!!.estado)
        } finally {
            resolver.delete(uri,null,null)
            SolicitudAdopcionManager.clear();session.clear()
        }
    }
}
