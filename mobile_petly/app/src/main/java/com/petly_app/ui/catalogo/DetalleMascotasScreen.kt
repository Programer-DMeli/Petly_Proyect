package com.petly_app.ui.catalogo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.ui.common.PreviewNotice

@Composable
fun DetalleMascotaScreen(state: DetalleMascotaUiState, preview: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("Volver al catálogo") }
        PreviewNotice(preview)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            }
            else -> state.mascota?.let { mascota ->
                MascotaPhoto(mascota.fotografiaUrl, mascota.nombre, Modifier.fillMaxWidth().height(240.dp))
                Text(mascota.nombre, style = MaterialTheme.typography.headlineLarge)
                Text("Estado: ${mascota.estado}", style = MaterialTheme.typography.titleMedium)
                Text("Especie: ${mascota.especie}")
                Text("Tamaño: ${mascota.tamano}")
                Text("Edad: ${mascota.edadMeses} meses")
                Text("Temperamento: ${mascota.temperamento}")
                Text("Energía: ${mascota.energia}")
                Text(mascota.descripcion)
                HorizontalDivider()
                Text("Albergue", style = MaterialTheme.typography.titleLarge)
                Text(mascota.albergue)
                Text(mascota.ubicacion)
            }
        }
    }
}
