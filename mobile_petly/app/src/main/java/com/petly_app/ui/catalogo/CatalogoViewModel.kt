package com.petly_app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.model.*
import com.petly_app.data.repository.CatalogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogoUiState(
    val filters: CatalogoFilters = CatalogoFilters(),
    val appliedFilters: CatalogoFilters = CatalogoFilters(),
    val options: CatalogoOptions? = null, val items: List<Mascota> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val filterError: String? = null, val page: Int = 1, val totalPages: Int = 1, val total: Int = 0
) {
    val pendingFilters: Boolean get() = filters != appliedFilters
}

class CatalogoViewModel(private val repository: CatalogoRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogoUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    init { applyFilters() }

    fun edit(filters: CatalogoFilters) { _state.update { it.copy(filters = filters, filterError = null) } }
    fun clearFilters() { edit(CatalogoFilters()); applyFilters() }
    fun applyFilters() { load(_state.value.filters, 1) }
    fun retry() { load(_state.value.appliedFilters, _state.value.page) }
    fun changePage(page: Int) {
        val state = _state.value
        if (!state.loading && !state.pendingFilters && page in 1..state.totalPages) load(state.appliedFilters, page)
    }

    private fun load(filters: CatalogoFilters, page: Int) {
        val error = filters.ageError()
        if (error != null) { _state.update { it.copy(filterError = error) }; return }
        job?.cancel()
        val current = ++generation
        _state.update { it.copy(loading = true, error = null, filterError = null, appliedFilters = filters, page = page) }
        job = viewModelScope.launch {
            try {
                val options = _state.value.options ?: repository.options()
                val result = repository.search(filters, page, 3)
                if (current == generation) _state.update { it.copy(options = options, items = result.items,
                    loading = false, total = result.total, page = result.page, totalPages = result.totalPages) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (current == generation) _state.update { it.copy(loading = false, error = apiErrorMessage(e)) }
            }
        }
    }
}
