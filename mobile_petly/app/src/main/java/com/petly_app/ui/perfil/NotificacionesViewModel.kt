package com.petly_app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.CriteriosAvisoDto
import com.petly_app.data.remote.NotificacionDto
import com.petly_app.data.remote.NotificacionesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val INTERVALO_NOTIFICACIONES_MS = 60_000L

// Los avisos llegan en ISO 8601 en la misma zona horaria, con fracción opcional.
private val masRecientesPrimero = compareByDescending<NotificacionDto> { it.ocurrido_en.removeSuffix("Z") }
    .thenByDescending { it.id }

data class NotificacionesState(
    val items: List<NotificacionDto> = emptyList(),
    val noLeidas: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val preview: Boolean = false,
    val criterios: CriteriosAvisoDto? = null,
    val criteriosGuardados: Boolean = false
)

class NotificacionesViewModel(
    private val api: NotificacionesApi,
    private val preview: Boolean = false
) : ViewModel() {
    private val mutable = MutableStateFlow(NotificacionesState(preview = preview))
    val state = mutable.asStateFlow()
    private val requests = Mutex()
    private var firstPageIds: List<Long>? = null
    private var page = 1

    suspend fun observe(inboxVisible: Boolean, pushUpdates: Flow<Unit>) = coroutineScope {
        if (preview) return@coroutineScope
        val updates = Channel<Unit>(Channel.CONFLATED)
        launch(start = CoroutineStart.UNDISPATCHED) {
            pushUpdates.collect { updates.trySend(Unit) }
        }
        while (true) {
            requests.withLock {
                // La consulta cubre los avisos recibidos antes de iniciarla.
                updates.tryReceive()
                val initialLoad = inboxVisible && firstPageIds == null && !mutable.value.busy
                if (initialLoad) mutable.update { it.copy(busy = true, error = null) }
                try {
                    if (inboxVisible) refreshFirstPage() else refreshCount()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (initialLoad) mutable.update { it.copy(error = apiErrorMessage(error)) }
                } finally {
                    if (initialLoad) mutable.update { it.copy(busy = false) }
                }
            }
            withTimeoutOrNull(INTERVALO_NOTIFICACIONES_MS) { updates.receive() }
        }
    }

    fun load(more: Boolean = false) = execute {
        if (more) loadOlder() else refreshFirstPage()
    }

    private suspend fun refreshFirstPage() {
        val response = api.listar()
        val ids = response.results.map { it.id }
        // La primera página puede desplazar los límites de las siguientes.
        if (firstPageIds != ids) page = 1
        firstPageIds = ids
        val hasNext = response.next != null
        val last = response.results.lastOrNull()
        mutable.update { state ->
            val older = if (hasNext && last != null) {
                state.items.filter { masRecientesPrimero.compare(it, last) > 0 }
            } else {
                emptyList()
            }
            state.copy(
                items = response.results + older,
                noLeidas = response.no_leidas,
                hasMore = if (page == 1) hasNext else state.hasMore,
                error = null
            )
        }
    }

    private suspend fun loadOlder() {
        if (!mutable.value.hasMore) return
        val known = mutable.value.items.mapTo(mutableSetOf()) { it.id }
        var target = page
        do {
            val response = api.listar(++target)
            val newItems = response.results.any { it.id !in known }
            if (newItems || response.next == null) {
                page = target
                mutable.update {
                    it.copy(
                        items = (response.results + it.items).distinctBy(NotificacionDto::id)
                            .sortedWith(masRecientesPrimero),
                        noLeidas = response.no_leidas,
                        hasMore = response.next != null
                    )
                }
                return
            }
        } while (true)
    }

    private suspend fun refreshCount() {
        val response = api.contarNoLeidas()
        mutable.update { it.copy(
            noLeidas = response.no_leidas
        ) }
    }

    fun loadCriterios() = execute {
        val criterios = api.criterios()
        mutable.update { it.copy(criterios = criterios, criteriosGuardados = false) }
    }

    fun saveCriterios(criterios: CriteriosAvisoDto) = execute {
        val saved = api.guardarCriterios(criterios)
        mutable.update { it.copy(criterios = saved, criteriosGuardados = true) }
    }

    fun read(id: Long) = execute {
        markRead(id)
    }

    suspend fun openNotice(id: Long): NotificacionDto = requests.withLock {
        val cached = mutable.value.items.any { it.id == id }
        val notice = markRead(id)
        if (!cached) {
            try {
                refreshCount()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
        notice
    }

    private suspend fun markRead(id: Long): NotificacionDto {
        val updated = api.leer(id)
        mutable.update { state ->
            val wasUnread = state.items.any { it.id == id && it.leido_en == null }
            state.copy(items = state.items.map { if (it.id == id) updated else it },
                noLeidas = (state.noLeidas - if (wasUnread) 1 else 0).coerceAtLeast(0))
        }
        return updated
    }

    fun readAll() = execute {
        api.leerTodas()
        val response = api.listar()
        page = 1
        firstPageIds = response.results.map { it.id }
        mutable.update { it.copy(items = response.results, noLeidas = response.no_leidas, hasMore = response.next != null) }
    }

    private fun execute(block: suspend () -> Unit) {
        if (preview || mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { requests.withLock { block() } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(error = apiErrorMessage(e)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
