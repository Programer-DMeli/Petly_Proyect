package com.petly_app.data.repository

import com.petly_app.data.model.CatalogoFilters
import com.petly_app.data.model.CatalogoOptions
import com.petly_app.data.model.Mascota
import com.petly_app.data.model.MascotaPage

interface CatalogoRepository {
    suspend fun options(): CatalogoOptions
    suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage
    suspend fun consejos(id: Long): com.petly_app.data.model.ConsejosIA = com.petly_app.data.model.ConsejosIA("NO_CONFIGURADO", "Consejos IA no disponibles en vista previa.")
    suspend fun detail(id: Long): Mascota?
}
