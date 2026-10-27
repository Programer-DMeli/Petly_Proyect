package com.petly_app.data.repository
import com.petly_app.data.remote.*
import com.petly_app.data.model.*
class RemotePerfilRepository(private val api: PerfilApi): PerfilRepository {
    override suspend fun load() = api.load().draft()
    override suspend fun save(draft: PerfilDraft) { api.save(PerfilDto.from(draft)) }
}
class RemoteCatalogoRepository(private val api: CatalogoApi): CatalogoRepository {
    override suspend fun options() = api.options()
    override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int) = api.search(
        filters.search.trim(),filters.especie,filters.tamano,filters.temperamento,
        filters.edadMinimaMeses.trim().toIntOrNull(), filters.edadMaximaMeses.trim().toIntOrNull(),page,pageSize)
    override suspend fun consejos(id: Long) = api.consejos(id)
    override suspend fun detail(id: Long) = api.detail(id)
}
