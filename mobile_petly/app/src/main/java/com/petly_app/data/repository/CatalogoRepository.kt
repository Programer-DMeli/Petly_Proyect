package com.petly_app.data.repository

import com.petly_app.data.model.CatalogoFilters
import com.petly_app.data.model.CatalogoOptions
import com.petly_app.data.model.Mascota
import com.petly_app.data.model.MascotaPage

interface CatalogoRepository {
    suspend fun options(): CatalogoOptions
    suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage
    suspend fun detail(id: Long): Mascota?
}
