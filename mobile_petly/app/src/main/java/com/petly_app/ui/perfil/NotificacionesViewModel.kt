package com.petly_app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.NotificacionDto
import com.petly_app.data.remote.NotificacionesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificacionesState(
    val items: List<NotificacionDto> = emptyList(),
    val noLeidas: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val preview: Boolean = false
)

class NotificacionesViewModel(
    private val api: NotificacionesApi,
    private val preview: Boolean = false
) : ViewModel() {
    private val mutable = MutableStateFlow(NotificacionesState(preview = preview))
    val state = mutable.asStateFlow()
    private var page = 1

    fun load(more: Boolean = false) = execute {
        if (more && !mutable.value.hasMore) return@execute
        val target = if (more) page + 1 else page
        var response = api.listar(if (more) target else 1)
        val rows = response.results.toMutableList()
        var loadedPage = if (more) target else 1
        if (!more) while (loadedPage < target && response.next != null) {
            response = api.listar(++loadedPage)
            rows.addAll(response.results)
        }
        page = loadedPage
        mutable.update { it.copy(
            items = (if (more) it.items + rows else rows).distinctBy { row -> row.id },
            noLeidas = response.no_leidas, hasMore = response.next != null
        ) }
    }

    fun read(id: Long) = execute {
        val updated = api.leer(id)
        mutable.update { state ->
            val wasUnread = state.items.any { it.id == id && it.leido_en == null }
            state.copy(items = state.items.map { if (it.id == id) updated else it },
                noLeidas = (state.noLeidas - if (wasUnread) 1 else 0).coerceAtLeast(0))
        }
    }

    fun readAll() = execute {
        api.leerTodas()
        val response = api.listar()
        page = 1
        mutable.update { it.copy(items = response.results, noLeidas = response.no_leidas, hasMore = response.next != null) }
    }

    private fun execute(block: suspend () -> Unit) {
        if (preview || mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(error = apiErrorMessage(e)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
