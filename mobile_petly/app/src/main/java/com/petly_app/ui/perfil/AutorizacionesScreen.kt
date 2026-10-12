package com.petly_app.ui.perfil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petly_app.data.model.AlbergueDto
@Composable
fun AutorizacionesScreen(state: AutorizacionState, onChange: (Long, Boolean) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    var confirming by remember { mutableStateOf<AlbergueDto?>(null) }
    confirming?.let { shelter -> AlertDialog(onDismissRequest = { confirming = null },
        title = { Text("Compartir con ${shelter.nombre}") },
        text = { Text("Autorizas a este albergue a consultar vivienda, horas fuera, presupuesto y convivencia. Podrás revocarlo cuando quieras. Esto no envía una solicitud de adopción.") },
        confirmButton = { TextButton(onClick = { confirming = null; onChange(shelter.id, true) }) { Text("Autorizar") } },
        dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancelar") } }) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("Volver") }; Text("Compartir mi perfil", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Elige expresamente quién puede consultar tus respuestas. Una revocación impide nuevas consultas.") }
        if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry, enabled = !state.busy) { Text("Reintentar") } } }
        if(!state.busy && state.error == null && state.shelters.isEmpty()) item { Text("Todavía no hay albergues disponibles.") }
        items(state.shelters, key = { it.id }) { shelter -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(shelter.nombre, style = MaterialTheme.typography.titleMedium); Text(shelter.direccion)
                if(shelter.id in state.allowed) OutlinedButton(onClick = { onChange(shelter.id, false) }, enabled = !state.busy) { Text("Revocar autorización") }
                else Button(onClick = { confirming = shelter }, enabled = !state.busy) { Text("Compartir perfil") }
            }
        } }
    }
}
