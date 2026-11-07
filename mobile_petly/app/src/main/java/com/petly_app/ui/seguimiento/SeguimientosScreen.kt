package com.petly_app.ui.seguimiento

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.ui.perfil.notificationDate

@Composable
fun SeguimientosScreen(
    state: SeguimientosState,
    onRefresh: () -> Unit,
    onSelect: (Long?) -> Unit,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Volver") }
            Text("Seguimiento postadopción", style = MaterialTheme.typography.headlineMedium)
            Text("Si tu mascota presenta problemas de adaptación, solicita asesoría veterinaria gratuita al albergue responsable.")
            if (!state.preview) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar seguimientos") }
        }
        if (state.preview) item { Text("Inicia sesión para consultar tus seguimientos y solicitar asesoría.") }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        state.success?.let { success -> item { Text(success) } }
        if (!state.preview && !state.busy && state.error == null && state.items.isEmpty()) item {
            Text("Todavía no tienes seguimientos. El albergue los programa después de la entrega de tu mascota.")
        }
        items(state.items, key = { it.id }) { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.mascota_nombre, style = MaterialTheme.typography.titleLarge)
                    Text("Albergue: ${row.albergue_nombre}")
                    Text("${if (row.modalidad == "VISITA") "Visita" else "Llamada"} · ${notificationDate(row.programado_para)}")
                    Text("Estado: ${when (row.estado) { "PROGRAMADO" -> "Programado"; "REALIZADO" -> "Realizado"; "CANCELADO" -> "Cancelado"; else -> row.estado }}")
                    row.asesorias_veterinarias.forEach { asesoria ->
                        Text("Asesoría solicitada · ${notificationDate(asesoria.creado_en)}", style = MaterialTheme.typography.labelLarge)
                        Text(asesoria.problema)
                    }
                    if (row.estado != "CANCELADO") {
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
