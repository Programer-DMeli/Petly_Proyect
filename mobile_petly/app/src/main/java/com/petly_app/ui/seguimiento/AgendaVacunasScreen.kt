package com.petly_app.ui.seguimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AgendaVacunasScreen(viewModel: AgendaVacunasViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val agenda = state.agenda
    val editor = state.editor
    val lista = rememberLazyListState()
    var porRetirar by rememberSaveable { mutableStateOf<Long?>(null) }
    val vacunaPorRetirar = agenda?.vacunas?.firstOrNull { it.id == porRetirar }

    LaunchedEffect(editor?.id, editor != null) {
        if (editor != null) lista.scrollToItem(0)
    }

    LazyColumn(
        state = lista, modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("Volver") }
                Text("Agenda de vacunas", style = MaterialTheme.typography.headlineMedium)
                agenda?.let { Text(it.mascota_nombre, style = MaterialTheme.typography.titleLarge) }
                if (state.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.ocupado && agenda == null) Text("Cargando agenda…")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.exito?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (agenda != null && editor == null) {
                    Button(onClick = { viewModel.editar() }, enabled = !state.ocupado) { Text("Agregar vacuna") }
                }
                if (!state.ocupado && editor == null) {
                    TextButton(onClick = viewModel::cargar) {
                        Text(if (agenda == null) "Reintentar" else "Actualizar agenda")
                    }
                }
                if (agenda?.vacunas?.isEmpty() == true && editor == null) Text("Todavía no hay vacunas programadas.")
            }
        }
        if (editor != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (editor.id == null) "Nueva vacuna" else "Editar vacuna programada",
                        style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = editor.nombre, onValueChange = viewModel::cambiarNombre,
                        label = { Text("Nombre de la vacuna") },
                        supportingText = state.errorNombre?.let { mensaje -> { Text(mensaje) } },
                        enabled = !state.ocupado, isError = state.errorNombre != null,
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editor.fecha, onValueChange = viewModel::cambiarFecha,
                        label = { Text("Fecha programada") }, placeholder = { Text("AAAA-MM-DD") },
                        supportingText = { Text(state.errorFecha ?: "Formato AAAA-MM-DD") },
                        enabled = !state.ocupado, isError = state.errorFecha != null,
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::guardar, enabled = !state.ocupado) { Text("Guardar vacuna") }
                        TextButton(onClick = viewModel::cancelarEdicion, enabled = !state.ocupado) { Text("Cancelar") }
                    }
                }
            }
        }
        items(agenda?.vacunas.orEmpty(), key = { it.id }) { vacuna ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(vacuna.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(vacuna.fecha_programada.split('-').asReversed().joinToString("/"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.editar(vacuna) }, enabled = !state.ocupado && editor == null) {
                            Text("Editar")
                        }
                        TextButton(onClick = { porRetirar = vacuna.id }, enabled = !state.ocupado && editor == null) {
                            Text("Quitar")
                        }
                    }
                }
            }
        }
    }

    if (vacunaPorRetirar != null) {
        AlertDialog(
            onDismissRequest = { porRetirar = null }, title = { Text("Quitar vacuna programada") },
            text = { Text("¿Quieres quitar ${vacunaPorRetirar.nombre} de la agenda?") },
            confirmButton = {
                TextButton(onClick = { porRetirar = null; viewModel.eliminar(vacunaPorRetirar.id) }) { Text("Quitar vacuna") }
            },
            dismissButton = { TextButton(onClick = { porRetirar = null }) { Text("Cancelar") } },
        )
    }
}
