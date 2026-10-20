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
    private val mascotas = listOf(
        Mascota(1, "Luna", "Perro", "Mediano", 24, "Tranquilo", "Media", "Le gusta pasear y descansar acompañada.", "Refugio Patitas Felices", "Lima", "Disponible", "https://images.unsplash.com/photo-1552053831-71594a27632d?q=80&w=1000"),
        Mascota(2, "Rocky", "Perro", "Grande", 36, "Activo", "Alta", "Fuerte, activo y muy juguetón.", "Refugio Patitas Felices", "Guadalajara", "Disponible", "https://images.unsplash.com/photo-1589941013453-ec89f33b5e95?q=80&w=1000"),
        Mascota(3, "Milo", "Gato", "Pequeño", 8, "Tranquilo", "Baja", "Tierno gatito al que le encanta dormir.", "Hogar de prueba", "Lima", "Adoptado", "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1000"),
        Mascota(4, "Canela", "Perro", "Pequeño", 18, "Sociable", "Media", "Perrita alegre e ideal para familias.", "Refugio Patitas Felices", "Guadalajara", "Disponible", "https://images.unsplash.com/photo-1543466835-00a7907e9de1?q=80&w=1000"),
        Mascota(5, "Simba", "Gato", "Mediano", 18, "Sociable", "Media", "Gato curioso y amigable.", "Hogar de prueba", "Lima", "Disponible", "https://images.unsplash.com/photo-1573865526739-10659fec78a5?q=80&w=1000"),
        Mascota(6, "Bella", "Perro", "Grande", 48, "Tranquilo", "Baja", "Labradora noble y amorosa.", "Hogar de prueba", "Guadalajara", "Adoptado", "https://images.unsplash.com/photo-1534361960057-19889db9621e?q=80&w=1000"),
        Mascota(7, "Thor", "Perro", "Mediano", 30, "Activo", "Alta", "Le encanta correr al aire libre.", "Refugio Patitas Felices", "Lima", "Disponible", "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?q=80&w=1000"),
        Mascota(8, "Nala", "Gato", "Pequeño", 12, "Tranquilo", "Media", "Gatita siamesa muy cariñosa.", "Hogar de prueba", "Guadalajara", "Disponible", "https://images.unsplash.com/photo-1513245543132-31f507417b26?q=80&w=1000")
    )
    override suspend fun options() = CatalogoOptions(listOf("Perro", "Gato"), listOf("Pequeño", "Mediano", "Grande"), listOf("Tranquilo", "Activo", "Sociable", "Por evaluar"))
    override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage {
        delay(200)
        return DemoCatalogoQuery.search(mascotas, filters, page, pageSize)
    }
    override suspend fun detail(id: Long): Mascota? { delay(200); return mascotas.find { it.id == id } }
}
