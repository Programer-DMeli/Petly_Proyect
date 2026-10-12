package com.petly_app.ui.perfil
import androidx.lifecycle.*
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.model.*
import com.petly_app.data.repository.AutorizacionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class AutorizacionState(val shelters: List<AlbergueDto> = emptyList(), val allowed: Set<Long> = emptySet(),
    val busy: Boolean = false, val error: String? = null)
class AutorizacionViewModel(private val repository: AutorizacionRepository): ViewModel() {
    private val mutable = MutableStateFlow(AutorizacionState())
    val state = mutable.asStateFlow()
    init { load() }
    fun load() = execute { mutable.value = AutorizacionState(repository.albergues(), repository.list().map { it.albergueId }.toSet()) }
    fun change(id: Long, allow: Boolean) = execute {
        if (allow) repository.allow(id) else repository.revoke(id)
        mutable.update { it.copy(allowed = repository.list().map { a -> a.albergueId }.toSet()) }
    }
    private fun execute(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.update { it.copy(error = apiErrorMessage(e)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
