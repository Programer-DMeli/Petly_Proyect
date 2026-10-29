package com.petly_app.data.repository
import com.petly_app.BuildConfig
import com.petly_app.data.remote.*
import com.petly_app.data.model.*
import java.net.URI
class RemotePerfilRepository(private val api: PerfilApi): PerfilRepository {
    override suspend fun load() = api.load().draft()
    override suspend fun save(draft: PerfilDraft) { api.save(PerfilDto.from(draft)) }
}
class RemoteCatalogoRepository(private val api: CatalogoApi): CatalogoRepository {
    override suspend fun options() = api.options()
    override suspend fun search(filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage {
        val result = api.search(
            filters.search.trim(),filters.especie,filters.tamano,filters.temperamento,
            filters.edadMinimaMeses.trim().toIntOrNull(), filters.edadMaximaMeses.trim().toIntOrNull(),page,pageSize)
        return result.copy(items = result.items.map(::resolvePhotoHost))
    }
    override suspend fun consejos(id: Long) = api.consejos(id)
    override suspend fun detail(id: Long) = api.detail(id)?.let(::resolvePhotoHost)

    private fun resolvePhotoHost(pet: Mascota): Mascota {
        return pet.copy(fotografiaUrl = CatalogPhotoUrl.resolve(
            pet.fotografiaUrl, BuildConfig.API_BASE_URL, BuildConfig.ADMIN_API_BASE_URL
        ))
    }
}

/** Repoints local Django media links to the host/port reachable by this Android build. */
internal object CatalogPhotoUrl {
    private val localHosts = setOf("10.0.2.2", "localhost", "127.0.0.1")

    fun resolve(photo: String?, apiBaseUrl: String, adminBaseUrl: String): String? {
        if (photo.isNullOrBlank()) return photo
        val photoUri = runCatching { URI(photo) }.getOrNull() ?: return photo
        val photoHost = photoUri.host?.lowercase() ?: return photo
        if (photoUri.rawPath?.contains("/media/") != true || photoHost !in localHosts) return photo

        val apiUri = runCatching { URI(apiBaseUrl) }.getOrNull() ?: return photo
        val apiHost = apiUri.host ?: return photo
        if (apiHost.lowercase() in localHosts) return photo
        val mediaUri = runCatching { URI(adminBaseUrl) }.getOrNull() ?: return photo
        val port = mediaUri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        return "${apiUri.scheme}://$apiHost$port${photoUri.rawPath}" +
            (photoUri.rawQuery?.let { "?$it" } ?: "") +
            (photoUri.rawFragment?.let { "#$it" } ?: "")
    }
}
