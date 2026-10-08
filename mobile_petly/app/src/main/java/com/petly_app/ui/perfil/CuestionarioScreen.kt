package com.petly_app.ui.perfil

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petly_app.data.model.PerfilDraft
import com.petly_app.ui.common.PreviewNotice

@Composable
fun CuestionarioScreen(
    state: PerfilUiState, preview: Boolean,
    onEdit: (PerfilDraft) -> Unit, onSave: (Boolean) -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit
) {
    var confirmExit by remember { mutableStateOf(false) }
    val busy = state.loading || state.saving
    val requestBack = { if (state.dirty) confirmExit = true else onBack() }
    BackHandler { if (!state.saving) requestBack() }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("¿Salir sin guardar?") },
        text = { Text("Los últimos cambios del formulario se perderán.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } }
    )
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = requestBack, enabled = !state.saving) { Text("Volver al catálogo") }
        PreviewNotice(preview)
        Text("Tu estilo de vida", style = MaterialTheme.typography.headlineMedium)
        Text("Cuéntanos sobre tu hogar y convivencia.")
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            if (!state.dirty) OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Reintentar carga") }
        }
        FormField("Tipo de vivienda", state.form.vivienda, state.errors["vivienda"], !busy) {
            onEdit(state.form.copy(vivienda = it))
        }
        FormField("Horas fuera de casa al día", state.form.horasFuera, state.errors["horasFuera"], !busy, true) {
            onEdit(state.form.copy(horasFuera = it))
        }
        FormField("Presupuesto mensual (S/)", state.form.presupuesto, state.errors["presupuesto"], !busy, true) {
            onEdit(state.form.copy(presupuesto = it))
        }
        Text("Convivencia", style = MaterialTheme.typography.titleLarge)
        BooleanQuestion("¿Viven niños en tu hogar?", state.form.tieneNinos, state.errors["ninos"], !busy) {
            onEdit(state.form.copy(tieneNinos = it))
        }
        BooleanQuestion("¿Tienes otras mascotas?", state.form.tieneOtrasMascotas, state.errors["mascotas"], !busy) {
            onEdit(state.form.copy(tieneOtrasMascotas = it))
        }
        Text(if (state.form.finalizado) "Cuestionario finalizado" else "Puedes guardar respuestas incompletas como borrador.")
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = { onSave(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Guardando…" else "Finalizar cuestionario")
        }
        OutlinedButton(onClick = { onSave(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar borrador") }
    }
}

@Composable
private fun FormField(label: String, value: String, error: String?, enabled: Boolean, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        enabled = enabled, isError = error != null,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BooleanQuestion(label: String, value: Boolean?, error: String?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        Row(modifier = Modifier.selectableGroup()) {
            listOf(true to "Sí", false to "No").forEach { (choice, text) ->
                Row(modifier = Modifier.selectable(selected = value == choice, enabled = enabled,
                    role = Role.RadioButton, onClick = { onChange(choice) }).padding(end = 16.dp)) {
                    RadioButton(selected = value == choice, onClick = null, enabled = enabled)
                    Text(text, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
