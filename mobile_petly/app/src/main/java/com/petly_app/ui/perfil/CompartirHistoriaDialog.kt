package com.petly_app.ui.perfil

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.JsonParser
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.HistoriaAdopcionDto
import com.petly_app.data.remote.SolicitudPersistida
import com.petly_app.data.repository.HistoriasAdopcionRepository
import com.petly_app.data.repository.MAX_CARACTERES_HISTORIA
import com.petly_app.data.repository.MAX_FOTOS_HISTORIA
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
internal fun CompartirHistoriaDialog(
    solicitud: SolicitudPersistida,
    repository: HistoriasAdopcionRepository,
    onDismiss: () -> Unit,
    onEnviada: (HistoriaAdopcionDto) -> Unit,
) {
    var historia by rememberSaveable(solicitud.id) { mutableStateOf("") }
    var fotos by rememberSaveable(solicitud.id) { mutableStateOf(arrayListOf<String>()) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { elegidas ->
        val seleccion = elegidas.distinct()
        if (seleccion.size > MAX_FOTOS_HISTORIA) {
            error = "Selecciona hasta 5 fotos."
        } else if (seleccion.isNotEmpty()) {
            fotos = ArrayList(seleccion.map { it.toString() })
            error = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!enviando) onDismiss() },
        title = { Text("Compartir mi historia") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                solicitud.mascota_nombre?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text("El albergue revisará tu historia y tus fotos antes de publicarlas.")
                OutlinedTextField(
                    value = historia,
                    onValueChange = { if (it.length <= MAX_CARACTERES_HISTORIA) historia = it },
                    label = { Text("Tu historia") },
                    supportingText = { Text("${historia.length} / $MAX_CARACTERES_HISTORIA") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !enviando,
                )
                Text("Adjunta de 1 a 5 fotos JPEG o PNG. En conjunto pueden pesar hasta 20 MB.")
                if (fotos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(fotos, key = { _, uri -> uri }) { index, uri ->
                            AsyncImage(
                                model = Uri.parse(uri),
                                contentDescription = "Foto seleccionada ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { selector.launch(arrayOf("image/jpeg", "image/png")) },
                    enabled = !enviando,
                ) {
                    Text(if (fotos.isEmpty()) "Seleccionar fotos" else "Cambiar fotos (${fotos.size})")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !enviando) { Text("Cancelar") } },
        confirmButton = {
            Button(
                enabled = !enviando && historia.isNotBlank() && fotos.isNotEmpty(),
                onClick = {
                    enviando = true
                    error = null
                    scope.launch {
                        try {
                            onEnviada(repository.enviar(solicitud.id, historia, fotos.map(Uri::parse)))
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            error = mensajeErrorHistoria(exception)
                        } finally {
                            enviando = false
                        }
                    }
                },
            ) { Text(if (enviando) "Enviando…" else "Enviar para revisión") }
        },
    )
}

private fun mensajeErrorHistoria(error: Exception): String {
    if (error is IllegalArgumentException) return error.message ?: "Revisa el texto y las fotos."
    if (error is HttpException) {
        val detalle = runCatching {
            error.response()?.errorBody()?.use { body ->
                val campos = JsonParser.parseString(body.string()).asJsonObject
                listOf("detail", "solicitud_id", "historia", "fotos", "non_field_errors")
                    .firstNotNullOfOrNull { campo ->
                        val valor = campos.get(campo)
                        when {
                            valor == null -> null
                            valor.isJsonPrimitive -> valor.asString
                            valor.isJsonArray -> valor.asJsonArray.firstOrNull()?.asString
                            else -> null
                        }
                    }
            }
        }.getOrNull()
        if (!detalle.isNullOrBlank()) return detalle
        if (error.code() == 409) return "Ya hay una historia para esta adopción."
        if (error.code() >= 500) return "No se pudo enviar tu historia. Inténtalo de nuevo."
    }
    return apiErrorMessage(error)
}
