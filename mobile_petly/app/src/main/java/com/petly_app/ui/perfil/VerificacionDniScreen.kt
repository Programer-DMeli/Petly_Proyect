package com.petly_app.ui.perfil

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun VerificacionDniScreen(
    state: KycUiState,
    edit: (String?, String?, String?) -> Unit,
    selectFront: (Uri?) -> Unit,
    selectBack: (Uri?) -> Unit,
    upload: () -> Unit,
    consultar: () -> Unit,
    retry: () -> Unit,
    onBack: () -> Unit
) {
    var pickingFront by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (pickingFront) selectFront(uri) else selectBack(uri) }
    val editable = !state.saving && state.record?.estado !in setOf("VERIFICADO", "APROBADO_MANUAL", "RECHAZADO_MANUAL", "PENDIENTE_REVISION")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Volver al perfil") }
        Text("Verificación de identidad", style = MaterialTheme.typography.headlineSmall)
        Text("Ingresa los datos del DNI y adjunta ambas caras. Los documentos se envían a un almacenamiento privado.")
        if (state.loading) CircularProgressIndicator()
        OutlinedTextField(state.dni, { edit(it.take(8), null, null) }, label = { Text("DNI") }, enabled = editable, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.nombres, { edit(null, it, null) }, label = { Text("Nombres completos") }, enabled = editable, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.apellidos, { edit(null, null, it) }, label = { Text("Apellidos completos") }, enabled = editable, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickingFront = true; picker.launch("image/*") }, enabled = editable) { Text("Elegir anverso") }
            Button(onClick = { pickingFront = false; picker.launch("image/*") }, enabled = editable) { Text("Elegir reverso") }
        }
        state.anverso?.let { AsyncImage(model = it, contentDescription = "Anverso del DNI", modifier = Modifier.size(120.dp)) }
        state.reverso?.let { AsyncImage(model = it, contentDescription = "Reverso del DNI", modifier = Modifier.size(120.dp)) }
        state.record?.let { Text("Estado: ${it.estado}") }
        state.resultado?.let { Text("Resultado: ${it.estado}${if (it.referencia.isNotBlank()) " · referencia ${it.referencia}" else ""}") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = upload, enabled = editable && !state.saving) { Text(if (state.saving) "Procesando…" else "Enviar documentos") }
        OutlinedButton(onClick = consultar, enabled = state.record != null && !state.saving && state.record.estado == "PENDIENTE") { Text("Consultar identidad") }
        TextButton(onClick = retry) { Text("Actualizar estado") }
    }
}
