package com.petly_app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    BackHandler(enabled = state.busy) { /* Esperar a que termine la petición. */ }
    LaunchedEffect(state.registered) {
        if (state.registered) {
            password = ""
            confirmation = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineMedium)
        if (state.registered) {
            Text("Cuenta creada. Ya puedes iniciar sesión.")
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Correo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmation, onValueChange = { confirmation = it },
                label = { Text("Repetir contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onRegister(email, password, confirmation) },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.busy) "Creando cuenta…" else "Registrarme") }
        }
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Volver al acceso") }
    }
}
