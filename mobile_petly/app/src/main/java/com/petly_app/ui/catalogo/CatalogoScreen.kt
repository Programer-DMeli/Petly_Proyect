package com.petly_app.ui.catalogo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petly_app.data.model.CatalogoFilters
import com.petly_app.ui.common.PreviewNotice

@Composable
fun CatalogoScreen(
    state: CatalogoUiState, preview: Boolean, onEdit: (CatalogoFilters) -> Unit,
    onApply: () -> Unit, onClear: () -> Unit, onPage: (Int) -> Unit,
    onRetry: () -> Unit, onDetail: (Long) -> Unit, onProfile: () -> Unit, onExit: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Petly", style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onProfile) { Text("Mi cuestionario") }
                TextButton(onClick = onExit) { Text(if (preview) "Salir de prueba" else "Cerrar sesión") }
            }
        }
        item { PreviewNotice(preview) }
        item { Text("Encuentra a tu compañero", style = MaterialTheme.typography.headlineSmall) }
        item {
            OutlinedTextField(value = state.filters.search, onValueChange = { onEdit(state.filters.copy(search = it)) },
                label = { Text("Buscar por nombre o albergue") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        state.options?.let { options ->
            item { FilterOptions("Especie", options.especies, state.filters.especie) { onEdit(state.filters.copy(especie = it)) } }
            item { FilterOptions("Tamaño", options.tamanos, state.filters.tamano) { onEdit(state.filters.copy(tamano = it)) } }
            item { FilterOptions("Temperamento", options.temperamentos, state.filters.temperamento) { onEdit(state.filters.copy(temperamento = it)) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = state.filters.edadMinimaMeses,
                    onValueChange = { onEdit(state.filters.copy(edadMinimaMeses = it)) }, label = { Text("Edad mín. (meses)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = state.filters.edadMaximaMeses,
                    onValueChange = { onEdit(state.filters.copy(edadMaximaMeses = it)) }, label = { Text("Edad máx. (meses)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            state.filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onApply, enabled = !state.loading) { Text("Buscar") }
                OutlinedButton(onClick = onClear, enabled = !state.loading) { Text("Limpiar") }
            }
            if (state.pendingFilters) Text("Pulsa Buscar para aplicar los cambios.")
        }
        when {
            state.loading -> item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); Text("Cargando mascotas…") }
            state.error != null -> item {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            }
            state.items.isEmpty() -> item { Text("Sin resultados. Prueba con otros filtros.") }
            else -> {
                item { Text("${state.total} mascotas encontradas") }
                items(state.items, key = { it.id }) { mascota ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        MascotaPhoto(mascota.fotografiaUrl, mascota.nombre, Modifier.fillMaxWidth().height(160.dp))
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(mascota.nombre, style = MaterialTheme.typography.titleLarge)
                            Text("${mascota.especie} · ${mascota.edadMeses} meses · ${mascota.tamano}")
                            Text("Temperamento: ${mascota.temperamento}")
                            Text("Estado: ${mascota.estado}")
                            Text(mascota.albergue)
                            Button(onClick = { onDetail(mascota.id) }) { Text("Ver detalle") }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Página ${state.page} de ${state.totalPages}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { onPage(state.page - 1) }, enabled = state.page > 1 && !state.pendingFilters) { Text("Anterior") }
                            OutlinedButton(onClick = { onPage(state.page + 1) }, enabled = state.page < state.totalPages && !state.pendingFilters) { Text("Siguiente") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOptions(title: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Todos") }) }
            items(options, key = { it }) { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
            }
        }
    }
}

