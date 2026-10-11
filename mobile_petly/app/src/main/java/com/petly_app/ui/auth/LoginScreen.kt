package com.petly_app.ui.auth

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
fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onCheckConnection: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Petly", style = MaterialTheme.typography.headlineLarge)
        Text("Ingresa para comenzar tu búsqueda.")
        state.connectionMessage?.let { Text(it) }
        OutlinedButton(
            onClick = onCheckConnection,
            enabled = !state.checkingConnection && !state.busy
        ) {
            Text(if (state.checkingConnection) "Comprobando…" else "Comprobar conexión")
        }
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
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onLogin(email, password) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.busy) "Ingresando…" else "Ingresar") }
        TextButton(onClick = onRegister, enabled = !state.busy) { Text("Crear cuenta") }
    }
}
