package com.petly_app.ui.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petly_app.data.remote.CriteriosAvisoDto
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
    onBack: (() -> Unit)? = null,
    onOpenPet: (Long) -> Unit = {},
    onLoadCriterios: () -> Unit = {},
    onSaveCriterios: (CriteriosAvisoDto) -> Unit = {}
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            onBack?.let { TextButton(onClick = it) { Text("Volver") } }
            Text("Notificaciones", style = MaterialTheme.typography.headlineMedium)
            Text("Conoce nuevas mascotas y sigue tus solicitudes de adopción.")
            if (!state.preview) {
                TextButton(onClick = { editing = !editing; if (editing) onLoadCriterios() }, enabled = !state.busy) {
                    Text(if (editing) "Cerrar criterios" else "Avisos de nuevas mascotas")
                }
                Text("${state.noLeidas} sin leer")
                Row {
                    TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar") }
                    TextButton(onClick = onReadAll, enabled = !state.busy && state.noLeidas > 0) {
                        Text("Marcar todas como leídas")
                    }
                }
            }
        }
        if (editing && !state.preview) item {
            state.criterios?.let { criterios ->
                CriteriosAvisoForm(criterios, state.busy, onSaveCriterios)
            }
            if (state.criteriosGuardados) Text("Criterios guardados. Recibirás avisos de los próximos ingresos que coincidan.")
        }
        if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.error?.let { error -> item {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { if (editing) onLoadCriterios() else onRefresh() }, enabled = !state.busy) { Text("Reintentar") }
        } }
        if (state.preview) item {
            Text("Inicia sesión para recibir avisos de mascotas y solicitudes.")
        } else if (!state.busy && state.error == null && state.items.isEmpty()) item {
            Text("Todavía no tienes notificaciones. Guarda tus criterios para conocer nuevas mascotas y recibe novedades de tus solicitudes.")
        }
        items(state.items, key = { it.id }) { item ->
            Card(
                onClick = { if (item.leido_en == null) onRead(item.id); onOpenPet(item.mascota_id) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (item.leido_en == null)
                    MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (item.leido_en == null) "Nueva · ${item.titulo}" else item.titulo,
                        fontWeight = FontWeight.Bold)
                    Text("${item.mascota_nombre} · ${item.albergue_nombre}")
                    if (item.solicitud_id != null) Text("Solicitud #${item.solicitud_id} · Estado actual: ${item.estado_actual}",
                        style = MaterialTheme.typography.labelMedium)
                    Text(item.mensaje)
                    if (item.descripcion.isNotBlank()) Text("Observaciones: ${item.descripcion}")
                    Text(notificationDate(item.ocurrido_en), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { if (item.leido_en == null) onRead(item.id); onOpenPet(item.mascota_id) }) {
                        Text("Ver mascota")
                    }
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

@Composable
private fun CriteriosAvisoForm(initial: CriteriosAvisoDto, busy: Boolean, onSave: (CriteriosAvisoDto) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var minimum by remember(initial) { mutableStateOf(initial.edad_min_meses?.toString().orEmpty()) }
    var maximum by remember(initial) { mutableStateOf(initial.edad_max_meses?.toString().orEmpty()) }
    val low = minimum.toIntOrNull()
    val high = maximum.toIntOrNull()
    val valid = (minimum.isBlank() || low != null && low in 0..600) &&
        (maximum.isBlank() || high != null && high in 0..600) && (low == null || high == null || low <= high)
    val hasCriteria = draft.especie.isNotBlank() || draft.tamanio.isNotBlank() || draft.temperamento.isNotBlank() || low != null || high != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mis criterios guardados", style = MaterialTheme.typography.titleMedium)
        Text("Se deben cumplir todos los filtros elegidos. Los campos vacíos aceptan cualquier valor. Los avisos aparecen en esta bandeja al abrir la app.")
        Row { Checkbox(draft.activo, { draft = draft.copy(activo = it) }, enabled = !busy); Text("Activar avisos") }
        CriterioSelector("Especie", draft.especie, listOf("PERRO" to "Perro", "GATO" to "Gato", "OTRO" to "Otro"), !busy) { draft = draft.copy(especie = it) }
        CriterioSelector("Tamaño", draft.tamanio, listOf("PEQUENO" to "Pequeño", "MEDIANO" to "Mediano", "GRANDE" to "Grande"), !busy) { draft = draft.copy(tamanio = it) }
        CriterioSelector("Temperamento", draft.temperamento, listOf("TRANQUILO" to "Tranquilo", "ACTIVO" to "Activo", "JUGUETON" to "Juguetón", "TIMIDO" to "Tímido", "SOCIABLE" to "Sociable", "INDEPENDIENTE" to "Independiente"), !busy) { draft = draft.copy(temperamento = it) }
        OutlinedTextField(minimum, { minimum = it }, label = { Text("Edad mínima (meses)") }, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maximum, { maximum = it }, label = { Text("Edad máxima (meses)") }, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (!valid) Text("Usa edades entre 0 y 600 meses; la mínima no debe superar la máxima.", color = MaterialTheme.colorScheme.error)
        if (draft.activo && !hasCriteria) Text("Elige al menos un criterio para activar los avisos.")
        Button(onClick = { onSave(draft.copy(edad_min_meses = low, edad_max_meses = high)) }, enabled = !busy && valid && (!draft.activo || hasCriteria)) { Text("Guardar criterios") }
    }
}

@Composable
private fun CriterioSelector(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Cualquiera"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            (listOf("" to "Cualquiera") + options).forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false })
            }
        }
    }
}
