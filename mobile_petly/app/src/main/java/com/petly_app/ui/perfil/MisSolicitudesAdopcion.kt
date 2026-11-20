package com.petly_app.ui.perfil

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.data.remote.HistorialSolicitud
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.data.repository.SolicitudAdopcionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MisSolicitudesAdopcion() {
    var solicitudes by remember { mutableStateOf<List<SolicitudPersistida>?>(null) }
    var seleccion by remember { mutableStateOf<SolicitudPersistida?>(null) }
    var error by remember { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }

    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var fichaPendiente by rememberSaveable { mutableStateOf<Long?>(null) }
    var descargando by remember { mutableStateOf(false) }
    var resultadoDescarga by remember { mutableStateOf<Boolean?>(null) }
    val guardarFicha = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val solicitudId = fichaPendiente
        fichaPendiente = null
        if (uri != null && solicitudId != null) {
            descargando = true
            scope.launch {
                var guardado = false
                try {
                    val api = checkNotNull(SolicitudAdopcionManager.fichaVeterinariaApi)
                    withContext(Dispatchers.IO) {
                        api.descargar(solicitudId).use { pdf ->
                            checkNotNull(resolver.openOutputStream(uri, "w")).use { output ->
                                pdf.byteStream().copyTo(output)
                            }
                        }
                        guardado = true
                    }
                    resultadoDescarga = true
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    resultadoDescarga = false
                } finally {
                    if (!guardado) withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                    }
                    descargando = false
                }
            }
        }
    }

    LaunchedEffect(intento) {
        error = false
        try {
            solicitudes = SolicitudAdopcionManager.listarSolicitudes()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = true
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mis solicitudes de adopción", style = MaterialTheme.typography.titleMedium)
            if (descargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Guardando ficha veterinaria…")
            }
            resultadoDescarga?.let { guardado ->
                Text(
                    if (guardado) "Ficha veterinaria guardada."
                    else "No se pudo guardar la ficha veterinaria. Inténtalo de nuevo.",
                    color = if (guardado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
            }
            val lista = solicitudes
            when {
                error -> {
                    Text("No se pudieron cargar tus solicitudes.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { intento++ }) { Text("Reintentar") }
                }
                lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                lista.isEmpty() -> Text("Todavía no has enviado solicitudes de adopción.")
                else -> lista.forEach { solicitud ->
                    Column {
                        Text(solicitud.mascota_nombre ?: "Solicitud #${solicitud.id}")
                        Text(nombreEstado(solicitud.estado), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { seleccion = solicitud }) { Text("Ver seguimiento") }
                        if (solicitud.estado.equals("ENTREGADA", ignoreCase = true)) {
                            TextButton(
                                enabled = !descargando && fichaPendiente == null,
                                onClick = {
                                    resultadoDescarga = null
                                    fichaPendiente = solicitud.id
                                    guardarFicha.launch("ficha-veterinaria-${solicitud.id}.pdf")
                                }
                            ) { Text("Descargar ficha veterinaria (PDF)") }
                        }
                    }
                }
            }
        }
    }

    seleccion?.let { solicitud ->
        HistorialDeSolicitud(
            solicitud,
            onUpdated = { estado ->
                solicitudes = solicitudes?.map { if (it.id == solicitud.id) it.copy(estado = estado) else it }
            },
            onDismiss = { seleccion = null }
        )
    }
}

@Composable
private fun HistorialDeSolicitud(
    solicitud: SolicitudPersistida,
    onUpdated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var historial by remember(solicitud.id) { mutableStateOf<HistorialSolicitud?>(null) }
    var error by remember(solicitud.id) { mutableStateOf(false) }
    var intento by remember(solicitud.id) { mutableIntStateOf(0) }
    LaunchedEffect(solicitud.id, intento) {
        error = false
        try {
            val api = checkNotNull(SolicitudAdopcionManager.api)
            historial = api.seguimiento(solicitud.id).also { onUpdated(it.estadoActual) }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(solicitud.mascota_nombre ?: "Solicitud #${solicitud.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nombreEstado(historial?.estadoActual ?: solicitud.estado))
                val eventos = historial?.eventos
                when {
                    error -> {
                        Text("No se pudo cargar el historial.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { intento++ }) { Text("Reintentar") }
                    }
                    eventos == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    eventos.isEmpty() -> Text("Aún no hay eventos de seguimiento.")
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(eventos, key = { it.id }) { evento ->
                            Text(evento.tipo_display, style = MaterialTheme.typography.titleSmall)
                            evento.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private fun nombreEstado(estado: String): String = when (estado) {
    "PENDIENTE" -> "Pendiente de revisión"
    else -> estado.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
}
