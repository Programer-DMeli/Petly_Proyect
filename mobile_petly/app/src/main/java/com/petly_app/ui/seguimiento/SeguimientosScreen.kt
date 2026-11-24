package com.petly_app.ui.seguimiento

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.ui.perfil.notificationDate
import com.petly_app.ui.perfil.OpinionesAlbergue
import com.petly_app.data.remote.EstadoResena
import com.petly_app.data.remote.ResenasApi
import com.petly_app.data.repository.admiteReporte

@Composable
fun SeguimientosScreen(
    state: SeguimientosState,
    onRefresh: () -> Unit,
    onSelect: (Long?) -> Unit,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onOpenReporte: ((Long) -> Unit)? = null,
    objetivoId: Long? = null,
    resenasApi: ResenasApi? = null,
) {
    val lista = rememberLazyListState()
    val resenasRegistradas = remember(resenasApi) { mutableStateMapOf<Long, EstadoResena>() }
    val indiceObjetivo = state.items.indexOfFirst { it.id == objetivoId }
    LaunchedEffect(objetivoId, indiceObjetivo) {
        if (indiceObjetivo >= 0) lista.scrollToItem(indiceObjetivo + 1)
    }
    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("Volver") }
                Text("Seguimiento postadopción", style = MaterialTheme.typography.headlineMedium)
                Text("Si tu mascota presenta problemas de adaptación, solicita asesoría técnica o veterinaria gratuita al albergue responsable.")
                if (!state.preview) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar seguimientos") }
                if (state.preview) Text("Inicia sesión para consultar tus seguimientos y solicitar asesoría.")
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.success?.let { Text(it) }
                if (!state.preview && !state.busy && state.error == null) {
                    if (state.items.isEmpty()) Text("Todavía no tienes seguimientos. El albergue los programa después de la entrega de tu mascota.")
                    if (objetivoId != null && indiceObjetivo < 0) Text("Esta actividad no está disponible.")
                }
            }
        }
        items(state.items, key = { it.id }) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (row.id == objetivoId) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) else CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.mascota_nombre, style = MaterialTheme.typography.titleLarge)
                    Text("Albergue: ${row.albergue_nombre}")
                    val modalidad = when (row.modalidad) {
                        "VISITA" -> "Visita"
                        "LLAMADA" -> "Llamada"
                        else -> "Entrega de reporte"
                    }
                    Text("$modalidad · ${notificationDate(row.programado_para)}")
                    when (row.estado_entrega) {
                        "PENDIENTE" -> Text("Entrega pendiente")
                        "VENCIDA" -> Text("Entrega vencida", color = MaterialTheme.colorScheme.error)
                        "ENVIADA" -> Text("Reporte enviado")
                        "FUERA_DE_PLAZO" -> Text("Enviado fuera de plazo", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Estado: ${when (row.estado) { "PROGRAMADO" -> "Programado"; "REALIZADO" -> "Realizado"; "CANCELADO" -> "Cancelado"; else -> row.estado }}")
                    val cierre = row.expediente_cierre?.get("cerrado_en")?.takeIf { it.isJsonPrimitive }?.asString
                    if (cierre != null) {
                        Text("Cierre de adopción validado", style = MaterialTheme.typography.titleSmall)
                        Text(notificationDate(cierre))
                        val albergueId = row.albergue_id
                        if (albergueId != null && albergueId > 0 && resenasApi != null) {
                            OpinionesAlbergue(albergueId, resenasApi, resenasRegistradas[albergueId]) {
                                resenasRegistradas[albergueId] = it
                            }
                        }
                    }
                    if (row.admiteReporte() && onOpenReporte != null) {
                        TextButton(onClick = { onOpenReporte(row.id) }) { Text("Abrir reporte") }
                    }
                    row.asesorias_veterinarias.forEach { asesoria ->
                        Text("Asesoría solicitada · ${notificationDate(asesoria.creado_en)}", style = MaterialTheme.typography.labelLarge)
                        Text(asesoria.problema)
                        Text("Estado de asesoría: ${when (asesoria.estado) {
                            "PENDIENTE" -> "Pendiente"
                            "EN_ATENCION" -> "En atención"
                            "ATENDIDA" -> "Atendida"
                            else -> asesoria.estado
                        }}")
                    }
                    if (row.estado != "CANCELADO" && row.expediente_cierre == null) {
                        if (state.selectedId == row.id) {
                            OutlinedTextField(
                                value = state.problema, onValueChange = onEdit,
                                label = { Text("Problema de adaptación") },
                                supportingText = { Text(state.fieldError ?: "${state.problema.length}/2000 caracteres") },
                                isError = state.fieldError != null,
                                enabled = !state.busy, minLines = 3, maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = onSubmit, enabled = !state.busy) { Text("Enviar solicitud gratuita") }
                            TextButton(onClick = { onSelect(null) }, enabled = !state.busy) { Text("Cancelar") }
                        } else {
                            TextButton(onClick = { onSelect(row.id) }, enabled = !state.busy) {
                                Text("Solicitar asesoría veterinaria gratuita")
                            }
                        }
                    }
                }
            }
        }
    }
}
