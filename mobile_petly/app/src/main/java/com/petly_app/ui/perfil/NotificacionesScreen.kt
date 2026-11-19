package com.petly_app.ui.perfil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petly_app.data.remote.CriteriosAvisoDto
import com.petly_app.data.remote.NotificacionDto
import com.petly_app.ui.screen.HomeIcons
import com.petly_app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val NotificationsBackground = lerp(Background, Secondary, 0.36f)
private val NotificationCardShape = RoundedCornerShape(24.dp)
private val CompletedNotificationTypes = setOf("APROBADA", "ENTREGADA", "CONTRATO_FIRMADO")

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
    onSaveCriterios: (CriteriosAvisoDto) -> Unit = {},
    onOpenReporte: (Long) -> Unit = {},
    onOpenSeguimiento: (Long) -> Unit = {}
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NotificationsBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                onBack?.let { TextButton(onClick = it) { Text("Volver") } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Secondary) {
                        Icon(HomeIcons.Bell, null, Modifier.padding(14.dp).size(28.dp), tint = PrimaryDark)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notificaciones", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Conoce nuevas mascotas y sigue tus solicitudes de adopción.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                if (!state.preview) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Secondary) {
                        Text("${state.noLeidas} sin leer", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRefresh, enabled = !state.busy,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Actualizar") }
                        OutlinedButton(onClick = onReadAll, enabled = !state.busy && state.noLeidas > 0,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Marcar todas como leídas") }
                    }
                    TextButton(
                        onClick = { editing = !editing; if (editing) onLoadCriterios() },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(HomeIcons.Dog, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (editing) "Cerrar criterios" else "Avisos de nuevas mascotas")
                    }
                }
            }
        }
        if (editing && !state.preview) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.criterios?.let { CriteriosAvisoForm(it, state.busy, onSaveCriterios) }
                    if (state.criteriosGuardados) Text(
                        if (state.criterios?.activo == true) "Avisos activados en Android y web."
                        else "Criterios guardados con los avisos desactivados.",
                        color = PrimaryDark, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.busy) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary, trackColor = Secondary)
                Text("Actualizando notificaciones…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        state.error?.let { error -> item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No pudimos actualizar los avisos", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { if (editing) onLoadCriterios() else onRefresh() }, enabled = !state.busy) {
                        Text("Reintentar")
                    }
                }
            }
        } }
        if (state.preview || (!state.busy && state.error == null && state.items.isEmpty())) item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(HomeIcons.Bell, null, Modifier.size(40.dp), tint = Primary)
                    Text(if (state.preview) "Tus novedades, en un solo lugar" else "Tu bandeja está al día",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(if (state.preview) "Inicia sesión para recibir avisos de mascotas y solicitudes."
                        else "Todavía no tienes notificaciones. Activa los avisos de nuevas mascotas y guarda al menos un criterio.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
        items(state.items, key = { it.id }) { item ->
            val esProgramacion = item.tipo == "SEGUIMIENTO_PROGRAMADO"
            val abrir = {
                if (item.leido_en == null) onRead(item.id)
                when {
                    esProgramacion && item.seguimiento_id != null -> onOpenSeguimiento(item.seguimiento_id)
                    item.seguimiento_id != null -> onOpenReporte(item.seguimiento_id)
                    else -> onOpenPet(item.mascota_id)
                }
            }
            NotificacionCard(item, state.busy, abrir, { onRead(item.id) })
        }
        if (state.hasMore) item {
            OutlinedButton(onClick = onMore, enabled = !state.busy, border = BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ver anteriores")
            }
        }
    }
}

@Composable
private fun NotificacionCard(item: NotificacionDto, busy: Boolean, onOpen: () -> Unit, onRead: () -> Unit) {
    val unread = item.leido_en == null
    val icon = when {
        item.tipo == "NUEVA_COINCIDENCIA" -> HomeIcons.Dog
        item.tipo.startsWith("VETERINARIA_") -> HomeIcons.CompanionHeart
        item.tipo in CompletedNotificationTypes -> HomeIcons.Check
        item.seguimiento_id != null || item.tipo == "RECOJO_PROGRAMADO" -> HomeIcons.Calendar
        else -> HomeIcons.Clipboard
    }
    val formattedDate = remember(item.ocurrido_en) { notificationDate(item.ocurrido_en) }
    Card(
        onClick = onOpen,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
            .shadow(6.dp, NotificationCardShape, clip = false, ambientColor = PrimaryDark, spotColor = Primary)
            .semantics { stateDescription = if (unread) "Sin leer" else "Leída" },
        shape = NotificationCardShape,
        colors = CardDefaults.cardColors(containerColor = Surface, disabledContainerColor = Surface),
        border = BorderStroke(if (unread) 2.dp else 1.dp, if (unread) Primary else Border)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = if (unread) Secondary else Background) {
                    Icon(icon, null, Modifier.padding(10.dp).size(24.dp), tint = Primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (unread) Box(Modifier.size(8.dp).background(Primary, CircleShape))
                        Text(if (unread) "Sin leer" else "Leída", style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                    }
                    Text(item.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryDark)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.mascota_nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = PrimaryDark)
                Text(item.albergue_nombre, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (item.solicitud_id != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Background, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Solicitud #${item.solicitud_id}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("Estado actual: ${item.estado_actual}", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
                    }
                }
            }
            Text(item.mensaje, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            if (item.tipo == "SEGUIMIENTO_PROGRAMADO") {
                item.programado_para?.let { Text("Fecha programada: ${fechaLimiteLima(it)}", color = PrimaryDark) }
            } else {
                item.fecha_limite?.let { Text("Fecha límite: ${fechaLimiteLima(it)}", color = PrimaryDark) }
            }
            if (item.descripcion.isNotBlank()) {
                Surface(shape = RoundedCornerShape(12.dp), color = Background, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Observaciones", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text(item.descripcion, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(HomeIcons.Clock, null, Modifier.size(16.dp), tint = TextSecondary)
                Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            HorizontalDivider(color = Border)
            Button(
                onClick = onOpen,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Surface,
                    disabledContainerColor = Border,
                    disabledContentColor = TextSecondary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(when {
                    item.tipo == "SEGUIMIENTO_PROGRAMADO" -> "Ver seguimiento"
                    item.seguimiento_id != null -> "Abrir reporte"
                    else -> "Ver mascota"
                })
                Spacer(Modifier.width(8.dp))
                Icon(HomeIcons.ArrowRight, null, Modifier.size(18.dp))
            }
            if (unread) TextButton(onClick = onRead, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(HomeIcons.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Marcar como leída")
            }
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
        Text("Recibirás un aviso cuando una nueva mascota cumpla todos los filtros elegidos. Los criterios se comparten con la web.")
        Row { Checkbox(draft.activo, { draft = draft.copy(activo = it) }, enabled = !busy); Text("Activar avisos") }
        CriterioSelector("Especie", draft.especie, listOf("PERRO" to "Perro", "GATO" to "Gato", "OTRO" to "Otro"), !busy) { draft = draft.copy(especie = it) }
        CriterioSelector("Tamaño", draft.tamanio, listOf("PEQUENO" to "Pequeño", "MEDIANO" to "Mediano", "GRANDE" to "Grande"), !busy) { draft = draft.copy(tamanio = it) }
        CriterioSelector("Temperamento", draft.temperamento, listOf("TRANQUILO" to "Tranquilo", "ACTIVO" to "Activo", "JUGUETON" to "Juguetón", "TIMIDO" to "Tímido", "SOCIABLE" to "Sociable", "INDEPENDIENTE" to "Independiente", "POR_EVALUAR" to "Por evaluar"), !busy) { draft = draft.copy(temperamento = it) }
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
