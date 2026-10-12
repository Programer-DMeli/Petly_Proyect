package com.petly_app.data.repository
import com.petly_app.data.remote.AutorizacionApi
class AutorizacionRepository(private val api: AutorizacionApi) {
    suspend fun albergues() = api.albergues()
    suspend fun list() = api.list()
    suspend fun allow(id: Long) { api.allow(id) }
    suspend fun revoke(id: Long) { api.revoke(id) }
}
