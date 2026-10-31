package com.petly_app.data.repository

import com.petly_app.data.model.Mascota
import com.petly_app.data.remote.FavoritosApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FavoritosManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private var generation = 0
    var api: FavoritosApi? = null
    private val _favorites = MutableStateFlow<List<Mascota>>(emptyList())
    val favorites = _favorites.asStateFlow()
    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()
    fun isFavorite(petId: Long) = _favorites.value.any { it.id == petId }
    suspend fun cargar() { val service=api ?: return; val epoch=generation; try { val rows=service.listar(); if(epoch==generation) { _favorites.value=rows; _error.value="" } } catch(e: Exception) { if(epoch==generation) _error.value="No se pudieron cargar favoritos. Reintenta." } }
    fun toggleFavorite(mascota: Mascota) = change(mascota, false)
    fun removeFavorite(petId: Long) { _favorites.value.firstOrNull { it.id==petId }?.let { change(it,true) } }
    private fun change(pet: Mascota, remove: Boolean) {
        val epoch=generation
        scope.launch { mutex.withLock {
            if(epoch!=generation) return@withLock
            val service=api
            if(service==null) { _error.value="Inicia sesión para guardar favoritos."; return@withLock }
            try {
                val response=if(remove || isFavorite(pet.id)) service.quitar(pet.id) else service.agregar(pet.id)
                if(!response.isSuccessful) throw IllegalStateException()
                cargar()
            } catch(e: Exception) { if(epoch==generation) _error.value="No se pudo guardar el favorito. Reintenta." }
        } }
    }
    fun clear() { generation++; api=null; _favorites.value=emptyList(); _error.value="" }
}
