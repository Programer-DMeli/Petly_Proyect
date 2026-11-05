package com.petly_app.ui.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun NotificacionesScreen(
    state: NotificacionesState,
    onRefresh: () -> Unit,
    onRead: (Long) -> Unit,
    onReadAll: () -> Unit,
    onMore: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            onBack?.let { TextButton(onClick = it) { Text("Volver") } }
            Text("Notificaciones", style = MaterialTheme.typography.headlineMedium)
            Text("Sigue el estado de tus solicitudes de adopción.")
            if (!state.preview) {
                Text("${state.noLeidas} sin leer")
                Row {
                    TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar") }
                    TextButton(onClick = onReadAll, enabled = !state.busy && state.noLeidas > 0) {
                        Text("Marcar todas como leídas")
                    }
                }
            }
        }
        if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.error?.let { error -> item {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Reintentar") }
        } }
        if (state.preview) item {
            Text("Inicia sesión para recibir avisos reales sobre tus solicitudes.")
        } else if (!state.busy && state.error == null && state.items.isEmpty()) item {
            Text("Todavía no tienes notificaciones. Aquí verás las novedades de tus solicitudes cuando envíes una.")
        }
        items(state.items, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (item.leido_en == null)
                    MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (item.leido_en == null) "Nueva · ${item.titulo}" else item.titulo,
                        fontWeight = FontWeight.Bold)
                    Text("${item.mascota_nombre} · ${item.albergue_nombre}")
                    Text("Solicitud #${item.solicitud_id} · Estado actual: ${item.estado_actual}",
                        style = MaterialTheme.typography.labelMedium)
                    Text(item.mensaje)
                    if (item.descripcion.isNotBlank()) Text("Observaciones: ${item.descripcion}")
                    Text(notificationDate(item.ocurrido_en), style = MaterialTheme.typography.bodySmall)
                    if (item.leido_en == null) TextButton(onClick = { onRead(item.id) }, enabled = !state.busy) {
                        Text("Marcar como leída")
                    }
                }
            }
        }
        if (state.hasMore) item {
            TextButton(onClick = onMore, enabled = !state.busy) { Text("Ver anteriores") }
        }
    }
}

internal fun notificationDate(value: String): String {
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
    // DRF devuelve UTC; se muestra en la zona horaria del dispositivo.
    val normalized = value.replace(Regex("\\.(\\d{3})\\d+"), ".$1")
    for (pattern in formats) {
        val parsed = runCatching { SimpleDateFormat(pattern, Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(normalized) }.getOrNull()
        if (parsed != null) return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed)
    }
    return value
}
