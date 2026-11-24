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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.petly_app.data.remote.EstadoResena
import com.petly_app.data.remote.HistorialSolicitud
import com.petly_app.data.remote.ResenasApi
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.data.repository.HistoriasAdopcion
import com.petly_app.data.repository.HistoriasAdopcionRepository
import com.petly_app.data.repository.SolicitudAdopcionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MisSolicitudesAdopcion(
    onOpenAgendaVacunas: ((Long) -> Unit)? = null,
    listarSolicitudes: suspend () -> List<SolicitudPersistida> = SolicitudAdopcionManager::listarSolicitudes,
    historiasRepository: HistoriasAdopcionRepository? = null,
    resenasApi: ResenasApi? = SolicitudAdopcionManager.resenasApi,
    consultarHistorial: suspend (Long) -> HistorialSolicitud = {
        checkNotNull(SolicitudAdopcionManager.api).seguimiento(it)
    },
) {
    var solicitudes by remember { mutableStateOf<List<SolicitudPersistida>?>(null) }
    var seleccion by remember { mutableStateOf<SolicitudPersistida?>(null) }
    val estadoResenas = remember(resenasApi) { mutableStateMapOf<Long, EstadoResena>() }
    var consultaResenas by remember { mutableIntStateOf(0) }
    var errorResenas by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    var historias by remember(historiasRepository) { mutableStateOf<HistoriasAdopcion?>(null) }
    var historiaSolicitudId by rememberSaveable { mutableStateOf<Long?>(null) }
    var intentoHistorias by remember { mutableIntStateOf(0) }
    var cargandoHistorias by remember { mutableStateOf(false) }
    var errorHistorias by remember { mutableStateOf(false) }
    val hayEntregadas = solicitudes?.any { it.estado.equals("ENTREGADA", ignoreCase = true) } == true
    val albergues = solicitudes.orEmpty().mapNotNull { it.albergue?.takeIf { id -> id > 0 } }.toSet()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { consultaResenas++ }
    LaunchedEffect(resenasApi, albergues, consultaResenas) {
        if (resenasApi == null) return@LaunchedEffect
        errorResenas = false
        albergues.forEach { id ->
            launch {
                try {
                    estadoResenas[id] = resenasApi.estado(id)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    errorResenas = true
                }
            }
        }
    }

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
            solicitudes = listarSolicitudes()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = true
        }
    }

    LaunchedEffect(historiasRepository, hayEntregadas, intentoHistorias) {
        if (historiasRepository == null || !hayEntregadas) return@LaunchedEffect
        cargandoHistorias = true
        errorHistorias = false
        try {
            val actualizadas = historiasRepository.cargar()
            historias = actualizadas
            if (historiaSolicitudId !in actualizadas.elegibles) historiaSolicitudId = null
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            errorHistorias = true
        } finally {
            cargandoHistorias = false
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
                        estadoResenas[solicitud.albergue]?.takeIf { it.registrada }?.let { ResenaRegistrada(it) }
                        if (solicitud.estado.equals("ENTREGADA", ignoreCase = true)) {
                            if (onOpenAgendaVacunas != null) {
                                TextButton(onClick = { onOpenAgendaVacunas(solicitud.id) }) { Text("Agenda de vacunas") }
                            }
                            TextButton(
                                enabled = !descargando && fichaPendiente == null,
                                onClick = {
                                    resultadoDescarga = null
                                    fichaPendiente = solicitud.id
                                    guardarFicha.launch("ficha-veterinaria-${solicitud.id}.pdf")
                                }
                            ) { Text("Descargar ficha veterinaria (PDF)") }
                            val estadoHistoria = historias?.enviadas?.get(solicitud.id)
                            if (estadoHistoria != null) {
                                Text("Historia de adopción · ${estadoHistoria.estado_display}")
                                if (estadoHistoria.estado == "RECHAZADO") {
                                    estadoHistoria.motivo_revision?.takeIf { it.isNotBlank() }?.let { motivo ->
                                        Text("Motivo del rechazo: $motivo", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            } else if (historias?.elegibles?.contains(solicitud.id) == true) {
                                TextButton(
                                    onClick = { historiaSolicitudId = solicitud.id },
                                    enabled = !cargandoHistorias && !errorHistorias,
                                ) { Text("Compartir mi historia") }
                            }
                        }
                    }
                }
            }
            if (errorResenas) TextButton(onClick = { consultaResenas++ }) { Text("Reintentar consulta de reseñas") }
            if (hayEntregadas && historiasRepository != null) {
                when {
                    cargandoHistorias -> Text("Cargando historias de adopción…")
                    errorHistorias -> {
                        Text("No se pudieron cargar tus historias de adopción.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { intentoHistorias++ }) { Text("Reintentar historias") }
                    }
                    historias?.enviadas?.isNotEmpty() == true ->
                        TextButton(onClick = { intentoHistorias++ }) { Text("Actualizar historias") }
                }
            }
        }
    }

    val solicitudHistoria = solicitudes?.firstOrNull { it.id == historiaSolicitudId }
    if (historiasRepository != null && solicitudHistoria != null &&
        historias?.elegibles?.contains(solicitudHistoria.id) == true
    ) {
        CompartirHistoriaDialog(
            solicitud = solicitudHistoria,
            repository = historiasRepository,
            onDismiss = { historiaSolicitudId = null },
            onEnviada = { enviada ->
                historias = historias?.let { actual ->
                    HistoriasAdopcion(actual.elegibles - enviada.solicitud_id, actual.enviadas + (enviada.solicitud_id to enviada))
                }
                historiaSolicitudId = null
            },
        )
    }

    seleccion?.let { solicitud ->
        HistorialDeSolicitud(
            solicitud,
            consultarHistorial = consultarHistorial,
            resenasApi = resenasApi,
            estadoResenas = estadoResenas,
            onRegistrada = { id, estado ->
                estadoResenas[id] = estado
                consultaResenas++
            },
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
    consultarHistorial: suspend (Long) -> HistorialSolicitud,
    resenasApi: ResenasApi?,
    estadoResenas: Map<Long, EstadoResena>,
    onRegistrada: (Long, EstadoResena) -> Unit,
    onUpdated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var historial by remember(solicitud.id) { mutableStateOf<HistorialSolicitud?>(null) }
    var error by remember(solicitud.id) { mutableStateOf(false) }
    var intento by remember(solicitud.id) { mutableIntStateOf(0) }
    LaunchedEffect(solicitud.id, intento) {
        error = false
        try {
            historial = consultarHistorial(solicitud.id).also { onUpdated(it.estadoActual) }
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
                val cierre = historial?.cerradoEn
                if (historial?.estadoActual == "ENTREGADA" && !cierre.isNullOrBlank()) {
                    Text("Cierre de adopción validado", style = MaterialTheme.typography.titleSmall)
                    Text(notificationDate(cierre))
                    val albergueId = historial?.albergueId ?: solicitud.albergue
                    if (albergueId != null && albergueId > 0 && resenasApi != null) {
                        OpinionesAlbergue(albergueId, resenasApi, estadoResenas[albergueId]) {
                            onRegistrada(albergueId, it)
                        }
                    }
                }
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
