package com.petly_app.data.repository

import com.petly_app.data.model.Mascota
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gestor reactivo de mascotas favoritas del adoptante (US-14).
 * Permite guardar, eliminar y consultar favoritos de forma persistente durante la sesión.
 */
object FavoritosManager {
    private val _favorites = MutableStateFlow<List<Mascota>>(emptyList())
    val favorites: StateFlow<List<Mascota>> = _favorites.asStateFlow()

    fun isFavorite(petId: Long): Boolean {
        return _favorites.value.any { it.id == petId }
    }

    fun toggleFavorite(mascota: Mascota) {
        _favorites.update { current ->
            if (current.any { it.id == mascota.id }) {
                current.filter { it.id != mascota.id }
            } else {
                current + mascota
            }
        }
    }

    fun removeFavorite(petId: Long) {
        _favorites.update { current ->
            current.filter { it.id != petId }
        }
    }

    fun clear() {
        _favorites.value = emptyList()
    }
}
