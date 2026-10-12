package com.petly_app.data.repository

import com.petly_app.data.model.*
import kotlinx.coroutines.delay

class BackendPendingException : Exception("Esta función todavía no está conectada al servidor.")
class PendingPerfilRepository : PerfilRepository {
    override suspend fun load(): PerfilDraft? = throw BackendPendingException()
    override suspend fun save(draft: PerfilDraft): Unit = throw BackendPendingException()
}
class PendingCatalogoRepository : CatalogoRepository {
    override suspend fun options(): CatalogoOptions = throw BackendPendingException()
    override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage = throw BackendPendingException()
    override suspend fun detail(id: Long): Mascota? = throw BackendPendingException()
}
class PreviewPerfilRepository : PerfilRepository {
    private var saved: PerfilDraft? = null
    override suspend fun load(): PerfilDraft? { delay(200); return saved }
    override suspend fun save(draft: PerfilDraft) { delay(200); saved = draft }
    fun reset() { saved = null }
}
class PreviewCatalogoRepository : CatalogoRepository {
    // Datos ficticios. Las categorías son provisionales hasta el acuerdo con A y B.
    private val mascotas = listOf(
        Mascota(1, "Luna", "Perro", "Mediano", 24, "Tranquilo", "Media", "Le gusta pasear y descansar acompañada.", "Refugio de prueba", "Lima", "Disponible"),
        Mascota(2, "Milo", "Gato", "Pequeño", 8, "Por evaluar", "Por evaluar", "Su temperamento está pendiente de evaluación.", "Refugio de prueba", "Lima", "Disponible"),
        Mascota(3, "Rocky", "Perro", "Grande", 48, "Activo", "Alta", "Disfruta de los paseos y el juego.", "Hogar de prueba", "Callao", "En Proceso"),
        Mascota(4, "Nina", "Gato", "Pequeño", 36, "Tranquilo", "Baja", "Prefiere espacios tranquilos.", "Hogar de prueba", "Callao", "Disponible"),
        Mascota(5, "Toby", "Perro", "Pequeño", 12, "Sociable", "Media", "Descripción ficticia para revisar la interfaz.", "Refugio de prueba", "Lima", "Adoptado"),
        Mascota(6, "Kira", "Perro", "Mediano", 60, "Por evaluar", "Por evaluar", "Evaluación de comportamiento pendiente.", "Refugio de prueba", "Lima", "Disponible"),
        Mascota(7, "Simba", "Gato", "Mediano", 18, "Sociable", "Media", "Información de muestra, sin una mascota real asociada.", "Hogar de prueba", "Callao", "Disponible")
    )
    override suspend fun options() = CatalogoOptions(listOf("Perro", "Gato"), listOf("Pequeño", "Mediano", "Grande"), listOf("Tranquilo", "Activo", "Sociable", "Por evaluar"))
    override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage {
        delay(200)
        return DemoCatalogoQuery.search(mascotas, filters, page, pageSize)
    }
    override suspend fun detail(id: Long): Mascota? { delay(200); return mascotas.find { it.id == id } }
}
