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
                Text("Edad: " + (mascota.edadMeses?.let { "${it / 12} años · ${it % 12} meses" } ?: "Por evaluar"))
                Text("Temperamento: ${mascota.temperamento}")
                Text("Energía: ${mascota.energia}")
                Text(mascota.descripcion)
                mascota.compatibilidad?.let { compatibilidad ->
                    HorizontalDivider()
                    Text("Tu compatibilidad", style = MaterialTheme.typography.titleLarge)
                    Text(compatibilidad.mensaje)
                    Text(compatibilidad.porcentaje?.let { "Puntaje: $it%" }
                        ?: "Información insuficiente para calcular el puntaje")
                    Text("Cobertura de datos: ${compatibilidad.cobertura}%")
                    compatibilidad.criterios.forEach { criterio ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(criterio.nombre, style = MaterialTheme.typography.titleMedium)
                                Text("Peso: ${criterio.peso}%")
                                Text(criterio.aporte?.let {
                                    "Aporte ponderado: ${String.format(java.util.Locale.getDefault(), "%.2f", it)} puntos"
                                } ?: "Aporte no calculable: faltan datos de compatibilidad")
                                Text(criterio.explicacion, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text("El total ponderado se redondea al entero más cercano. Es una orientación; el albergue decide cada adopción.",
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text("Albergue", style = MaterialTheme.typography.titleLarge)
                Text(mascota.albergue)
                Text(mascota.ubicacion)
            }
        }
    }
}
