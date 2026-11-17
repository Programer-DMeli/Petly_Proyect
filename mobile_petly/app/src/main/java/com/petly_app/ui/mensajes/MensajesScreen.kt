package com.petly_app.ui.mensajes

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.petly_app.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private const val ERROR_ACTUALIZACION = "No se pudieron actualizar los mensajes."

@Composable
fun MensajesScreen(
    api: MensajeriaApi,
    resolver: ContentResolver,
    solicitudes: List<Triple<Long, Long, String>>,
    onMessagesRead: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ConversacionDto>>(emptyList()) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var messages by remember { mutableStateOf<List<MensajeDto>>(emptyList()) }
    var lastReadId by remember(selected) { mutableLongStateOf(0L) }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var moderationBusy by remember { mutableStateOf(false) }
    var menuExpanded by remember(selected) { mutableStateOf(false) }
    var confirmBlock by remember(selected) { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFilename by remember { mutableStateOf("adjunto") }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runCatching { resolver.openOutputStream(uri)?.use { output -> pendingDownload?.let(output::write) } }
        pendingDownload = null
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { fileUri = it }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notifyMessagesRead by rememberUpdatedState(onMessagesRead)
    val conversationId = selected

    fun cambiarBloqueo(id: Long, bloquear: Boolean) {
        if (moderationBusy) return
        moderationBusy = true
        error = null
        scope.launch {
            try {
                if (bloquear) api.bloquear(id) else api.desbloquear(id)
                refreshRevision++
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = if (bloquear) "No se pudo bloquear." else "No se pudo desbloquear."
            } finally {
                moderationBusy = false
            }
        }
    }
    LaunchedEffect(api, lifecycle, conversationId, refreshRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var loadDetail = true
            var lastReceivedId = 0L
            var recipientReadId: Long? = null
            while (true) {
                try {
                    conversations = api.listar()
                    if (conversationId != null) {
                        if (loadDetail) {
                            val detail = api.detalle(conversationId)
                            if (selected != conversationId) return@repeatOnLifecycle
                            messages = detail.mensajes.sortedBy { it.id }
                            lastReceivedId = messages.lastOrNull { !it.propio }?.id ?: 0L
                            loadDetail = false
                        } else {
                            val cursor = messages.lastOrNull()?.id ?: 0L
                            val updates = api.nuevosMensajes(conversationId, cursor)
                            if (selected != conversationId) return@repeatOnLifecycle
                            if (updates.mensajes.isNotEmpty() || updates.leidosHastaId != recipientReadId) {
                                recipientReadId = updates.leidosHastaId
                                val incoming = updates.mensajes.sortedBy { it.id }
                                lastReceivedId = incoming.lastOrNull { !it.propio }?.id ?: lastReceivedId
                                val readThrough = recipientReadId ?: 0L
                                messages = (messages + incoming).map { message ->
                                    if (message.propio && message.leido != (message.id <= readThrough)) {
                                        message.copy(leido = message.id <= readThrough)
                                    } else {
                                        message
                                    }
                                }
                            }
                        }
                        if (lastReceivedId > lastReadId) {
                            api.marcarLeido(conversationId, mapOf("hasta_id" to lastReceivedId))
                            lastReadId = lastReceivedId
                            notifyMessagesRead()
                        }
                    }
                    if (error == ERROR_ACTUALIZACION) error = null
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    error = ERROR_ACTUALIZACION
                }
                delay(INTERVALO_MENSAJERIA_MS)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mensajes con albergues", style = MaterialTheme.typography.headlineSmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (selected == null) {
            if (solicitudes.isNotEmpty()) {
                Text("Inicia una conversación desde tus solicitudes", style = MaterialTheme.typography.titleMedium)
                solicitudes.forEach { (solicitudId, _, mascota) ->
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val created = api.crear(mapOf("solicitud_id" to solicitudId))
                                messages = emptyList()
                                selected = created.id
                            } catch (exception: Exception) {
                                if (exception is CancellationException) throw exception
                                error = "No se pudo abrir el chat de $mascota."
                            } finally {
                                busy = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Abrir chat · $mascota") }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations) { conversation ->
                    ElevatedCard(onClick = { messages = emptyList(); selected = conversation.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${conversation.nombreContacto} · ${conversation.mascota}", style = MaterialTheme.typography.titleMedium)
                            Text(conversation.ultimoMensaje?.texto?.takeIf { it.isNotBlank() } ?: conversation.solicitudId?.let { "Solicitud #$it" } ?: "Consulta general")
                            Text("Respuesta promedio: ${conversation.promedioRespuestaHoras?.let { "$it h" } ?: "sin datos"}")
                            if (conversation.noLeidos > 0) Text("${conversation.noLeidos} mensajes sin leer")
                        }
                    }
                }
                if (conversations.isEmpty()) item { Text("Tus conversaciones aparecerán aquí cuando un albergue abra el chat de tu solicitud.") }
            }
        } else {
            val activeConversation = conversations.firstOrNull { it.id == selected }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = null; messages = emptyList() }) { Text("← Conversaciones") }
                activeConversation?.let { conversation ->
                    Box {
                        TextButton(onClick = { menuExpanded = true }, enabled = !moderationBusy) {
                            Text("Opciones")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (conversation.bloqueadaPorMi) "Desbloquear" else "Bloquear") },
                                onClick = {
                                    menuExpanded = false
                                    if (conversation.bloqueadaPorMi) cambiarBloqueo(conversation.id, false)
                                    else confirmBlock = true
                                }
                            )
                        }
                    }
                    if (confirmBlock) {
                        AlertDialog(
                            onDismissRequest = { confirmBlock = false },
                            title = { Text("¿Bloquear a esta persona?") },
                            text = { Text("Ya no podrá enviarte mensajes. Puedes desbloquearla desde Opciones.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmBlock = false
                                    cambiarBloqueo(conversation.id, true)
                                }) { Text("Bloquear") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmBlock = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            }
            if (activeConversation?.bloqueada == true) {
                Text(if (activeConversation.puedeEnviar) "La otra persona no puede enviarte mensajes nuevos." else "Esta persona te bloqueó; no puedes enviarle mensajes.", color = MaterialTheme.colorScheme.error)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { message ->
                    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(if (message.propio) "Tú" else "Albergue", style = MaterialTheme.typography.labelMedium)
                        if (message.bienvenidaAutomatica) Text("Mensaje de bienvenida", style = MaterialTheme.typography.labelSmall)
                        if (message.texto.isNotBlank()) Text(message.texto)
                        if (message.propio && message.leido) Text("Leído", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        message.adjuntos.forEach { attachment -> TextButton(onClick = {
                            scope.launch { runCatching { pendingDownload = api.descargar(attachment.descarga).bytes(); pendingFilename = attachment.nombreOriginal; saveFile.launch(pendingFilename) }
                                .onFailure { error = "No se pudo descargar el adjunto." } }
                        }) { Text("Descargar ${attachment.nombreOriginal}") } }
                    } }
                }
            }
            OutlinedTextField(draft, { draft = it.take(4000) }, label = { Text("Escribe un mensaje") }, modifier = Modifier.fillMaxWidth(), enabled = activeConversation?.puedeEnviar != false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = activeConversation?.puedeEnviar != false, onClick = { filePicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }) { Text(if (fileUri == null) "Adjuntar" else "Adjunto listo") }
                Button(enabled = activeConversation?.puedeEnviar != false && !busy && (draft.isNotBlank() || fileUri != null), onClick = {
                    val id = selected ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val part = fileUri?.let { uri ->
                                val mime = resolver.getType(uri) ?: "image/jpeg"
                                require(mime in setOf("image/jpeg", "image/png", "application/pdf"))
                                val ext = when (mime) { "image/png" -> ".png"; "application/pdf" -> ".pdf"; else -> ".jpg" }
                                val temp = File.createTempFile("petly-chat-", ext)
                                resolver.openInputStream(uri)?.use { input -> temp.outputStream().use(input::copyTo) }
                                require(temp.length() <= 10 * 1024 * 1024) { "El archivo supera 10 MB." }
                                MultipartBody.Part.createFormData("archivo", temp.name, temp.asRequestBody(mime.toMediaTypeOrNull()))
                            }
                            api.enviar(id, draft.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull()), part)
                            draft = ""
                            fileUri = null
                            refreshRevision++
                        } catch (_: Exception) { error = "No se pudo enviar el mensaje. Reintenta." }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Enviando…" else "Enviar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selected?.let { id -> scope.launch { runCatching { api.reportar(id, mapOf("motivo" to "OTRO")); error = "Reporte enviado." }.onFailure { error = "No se pudo reportar." } } } }) { Text("Reportar") }
            }
        }
    }
}
