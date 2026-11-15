package com.petly_app.ui.perfil

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.repository.PlazoReporte
import com.petly_app.data.repository.ReportesSeguimientoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReportesSeguimientoScreen(repository: ReportesSeguimientoRepository, entrega: Long, onBack: () -> Unit) {
    Column(
        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Volver") }
        RecordatoriosReporte(repository, entrega)
    }
}

@Composable
fun RecordatoriosReporte(repository: ReportesSeguimientoRepository, entrega: Long? = null) {
    var plazos by remember { mutableStateOf(emptyList<PlazoReporte>()) }
    var seleccionado by remember { mutableStateOf<PlazoReporte?>(null) }
    var carga by remember { mutableIntStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviado by remember { mutableStateOf(false) }
    var enlacePendiente by remember(entrega) { mutableStateOf(entrega) }
    LaunchedEffect(repository, carga) {
        cargando = true
        error = null
        try {
            plazos = repository.pendientes()
            enlacePendiente?.let { id ->
                seleccionado = plazos.firstOrNull { it.seguimiento.id == id }
                if (seleccionado == null) error = "Esta entrega ya no admite un reporte."
                enlacePendiente = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudieron consultar los recordatorios."
        } finally {
            cargando = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reportes de seguimiento", style = MaterialTheme.typography.titleMedium)
        if (enviado) Text("Reporte enviado al albergue.")
        if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!cargando && error == null && plazos.isEmpty()) Text("No tienes reportes pendientes.")
        if (error == null) {
            for (plazo in plazos) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(plazo.seguimiento.mascota_nombre, style = MaterialTheme.typography.titleSmall)
                        Text(plazo.seguimiento.albergue_nombre)
                        Text("Fecha límite: ${fechaLimiteLima(plazo.fechaLimite)}")
                        if (plazo.seguimiento.estado_entrega == "VENCIDA") {
                            Text("Entrega vencida", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { seleccionado = plazo }, enabled = !cargando) {
                            Text("Abrir reporte")
                        }
                    }
                }
            }
        }
        TextButton(onClick = { carga++ }, enabled = !cargando) {
            Text(if (error == null) "Actualizar recordatorios" else "Reintentar")
        }
    }

    seleccionado?.let { plazo ->
        FormularioReporte(plazo, repository, onClose = { seleccionado = null }, onSent = {
            seleccionado = null
            enviado = true
            plazos = plazos.filterNot { it.seguimiento.id == plazo.seguimiento.id }
            carga++
        })
    }
}

@Composable
private fun FormularioReporte(
    plazo: PlazoReporte,
    repository: ReportesSeguimientoRepository,
    onClose: () -> Unit,
    onSent: () -> Unit
) {
    var bienestar by remember { mutableStateOf<String?>(null) }
    var observaciones by remember { mutableStateOf("") }
    var fotos by remember { mutableStateOf(emptyList<Uri>()) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { elegidas ->
        if (elegidas.isNotEmpty()) {
            fotos = elegidas.distinct()
            error = if (fotos.size > 5) "Selecciona hasta 5 fotos." else null
        }
    }
    AlertDialog(
        onDismissRequest = { if (!enviando) onClose() },
        title = { Text("Reporte de ${plazo.seguimiento.mascota_nombre}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fecha límite: ${fechaLimiteLima(plazo.fechaLimite)}")
                Text("Estado de bienestar")
                for ((valor, etiqueta) in ESTADOS_BIENESTAR) {
                    FilterChip(
                        selected = bienestar == valor, onClick = { bienestar = valor },
                        enabled = !enviando, label = { Text(etiqueta) }
                    )
                }
                OutlinedTextField(
                    value = observaciones, onValueChange = { if (it.length <= 2000) observaciones = it },
                    label = { Text("Observaciones (opcional)") }, enabled = !enviando,
                    modifier = Modifier.fillMaxWidth(), maxLines = 5
                )
                Text("Adjunta de 1 a 5 fotos JPEG o PNG.")
                TextButton(onClick = { selector.launch("image/*") }, enabled = !enviando) {
                    Text(if (fotos.isEmpty()) "Seleccionar fotos" else "Cambiar fotos (${fotos.size})")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !enviando) { Text("Cerrar") } },
        confirmButton = {
            Button(enabled = !enviando && bienestar != null && fotos.size in 1..5, onClick = {
                val estado = bienestar ?: return@Button
                enviando = true
                error = null
                scope.launch {
                    try {
                        repository.enviar(plazo.seguimiento.id, estado, observaciones, fotos)
                        onSent()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = if (e is IllegalArgumentException) e.message else apiErrorMessage(e)
                    } finally {
                        enviando = false
                    }
                }
            }) { Text(if (enviando) "Enviando…" else "Enviar reporte") }
        }
    )
}

private val ESTADOS_BIENESTAR = listOf(
    "EXCELENTE" to "Excelente", "BUENO" to "Bueno",
    "REGULAR" to "Requiere atención", "PREOCUPANTE" to "Preocupante"
)

internal fun fechaLimiteLima(value: String): String {
    // Spring devuelve sus fechas históricas en hora de Lima; el cronograma Django incluye zona.
    val instante = runCatching {
        val normalizado = value.replace(Regex("\\.(\\d{3})\\d+"), ".$1")
        val formato = if (normalizado.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
        java.text.SimpleDateFormat(formato, java.util.Locale.ROOT).apply { isLenient = false }
            .parse(normalizado)
    }.getOrNull()
    if (instante != null && (value.endsWith("Z") || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(value))) {
        val etiqueta = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("America/Lima")
        }.format(instante)
        return "$etiqueta (hora de Lima)"
    }
    val partes = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?""").matchEntire(value)
        ?: return value
    val (anio, mes, dia, hora) = partes.destructured
    return "$dia/$mes/$anio $hora (hora de Lima)"
}
