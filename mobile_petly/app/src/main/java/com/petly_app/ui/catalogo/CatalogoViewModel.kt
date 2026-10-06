package com.petly_app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.repository.UsuarioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Solo prepara el destino y comprueba el usuario. US-12 se implementará después.
data class CatalogoUiState(
    val loading: Boolean = false,
    val email: String? = null,
    val error: String? = null
)

class CatalogoViewModel(private val repository: UsuarioRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogoUiState())
    val state = _state.asStateFlow()

    init { loadUser() }

    fun loadUser() {
        if (_state.value.loading) return
        _state.value = CatalogoUiState(loading = true)
        viewModelScope.launch {
            try {
                val user = repository.currentUser()
                _state.value = CatalogoUiState(email = user.email)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = CatalogoUiState(error = apiErrorMessage(error))
            }
        }
    }

    fun logout() = repository.logout()
}
