package com.petly_app.ui.auth
import androidx.lifecycle.*
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.repository.RecuperacionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class RecuperacionState(val busy: Boolean = false, val error: String? = null, val message: String? = null, val resetComplete: Boolean = false)
class RecuperacionViewModel(private val repository: RecuperacionRepository): ViewModel() {
    private val mutable = MutableStateFlow(RecuperacionState())
    val state = mutable.asStateFlow()
    fun request(email: String) = execute { repository.request(email); mutable.update { it.copy(message = "Si el correo existe, recibirás un enlace de 15 minutos.") } }
    fun confirm(token: String, password: String, confirmation: String) {
        if(token.isBlank() || (password.length !in 8..72 || password.toByteArray(Charsets.UTF_8).size > 72) || password != confirmation) {
            mutable.update { it.copy(error = "Completa el enlace y dos contraseñas iguales de 8 a 72 caracteres.") }; return
        }
        execute { repository.confirm(token,password,confirmation); mutable.update { it.copy(resetComplete = true, message = "Contraseña actualizada. Inicia sesión nuevamente.") } }
    }
    private fun execute(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.update { it.copy(error = apiErrorMessage(e)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
