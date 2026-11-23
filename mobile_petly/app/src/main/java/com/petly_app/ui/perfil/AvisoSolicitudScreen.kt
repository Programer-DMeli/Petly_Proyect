package com.petly_app.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.data.remote.NotificacionDto
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Composable
fun AvisoSolicitudScreen(read: suspend (Long) -> NotificacionDto, event: Long, onBack: () -> Unit) {
    var notice by remember(event) { mutableStateOf<NotificacionDto?>(null) }
    var error by remember(event) { mutableStateOf<String?>(null) }
    var retry by remember(event) { mutableIntStateOf(0) }
    LaunchedEffect(event, retry) {
        error = null
        try {
            notice = read(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = if ((failure as? HttpException)?.code() == 404) "Este aviso no está disponible para tu cuenta."
            else "No se pudo cargar el aviso."
        }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("Volver") }
        val result = notice
        when {
            result != null -> {
                Text("Solicitud #${result.solicitud_id} · ${result.mascota_nombre}", style = MaterialTheme.typography.headlineSmall)
                Text(result.albergue_nombre)
                Text("Estado actual: ${result.estado_actual}")
                Text(result.titulo, style = MaterialTheme.typography.titleMedium)
                Text(result.mensaje)
                if (result.descripcion.isNotBlank()) Text(result.descripcion)
            }
            error != null -> {
                Text(error.orEmpty())
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            }
            else -> CircularProgressIndicator()
        }
    }
}
