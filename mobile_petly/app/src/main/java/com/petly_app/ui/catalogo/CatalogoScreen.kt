package com.petly_app.ui.catalogo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CatalogoScreen(state: CatalogoUiState, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Catálogo Petly", style = MaterialTheme.typography.headlineMedium)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Reintentar") }
            }
            else -> {
                Text("Sesión verificada: ${state.email.orEmpty()}")
                Text("El listado de mascotas y los filtros se agregarán en el siguiente entregable.")
            }
        }
        OutlinedButton(onClick = onLogout) { Text("Cerrar sesión") }
    }
}
