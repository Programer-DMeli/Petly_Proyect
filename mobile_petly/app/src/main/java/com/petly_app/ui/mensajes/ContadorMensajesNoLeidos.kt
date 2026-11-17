package com.petly_app.ui.mensajes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

internal const val INTERVALO_MENSAJERIA_MS = 5_000L

internal class ContadorMensajesNoLeidos(private val consultarTotal: suspend () -> Int) {
    private val actualizaciones = Channel<Unit>(Channel.CONFLATED)
    private val totalMutable = MutableStateFlow(0)
    val total = totalMutable.asStateFlow()

    fun actualizar() {
        actualizaciones.trySend(Unit)
    }

    suspend fun observar() {
        while (true) {
            // La consulta cubre las lecturas confirmadas antes de iniciarla.
            actualizaciones.tryReceive()
            try {
                totalMutable.value = consultarTotal()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Se conserva el último resultado hasta la próxima consulta.
            }
            withTimeoutOrNull(INTERVALO_MENSAJERIA_MS) { actualizaciones.receive() }
        }
    }
}
