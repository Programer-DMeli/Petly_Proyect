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

    BackHandler(enabled = state.busy) { /* Bloquear navegación mientras está procesando la solicitud. */ }

    LaunchedEffect(state.registered) {
        if (state.registered) {
            password = ""
            confirmation = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineMedium)

        if (state.registered) {
            Text(
                "¡Cuenta creada con éxito!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Tu registro se ha completado correctamente. Ya puedes iniciar sesión con tu correo y contraseña.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar sesión")
            }
        } else {
            Text("Ingresa tus datos para registrarte en Petly.")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            PasswordField(password, { password = it }, "Contraseña", !state.busy)
            PasswordField(confirmation, { confirmation = it }, "Repetir contraseña", !state.busy)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (state.rateLimitRemainingSeconds > 0) Text("Límite de peticiones. Vuelve a intentar en ${state.rateLimitRemainingSeconds} segundos.", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { onRegister(email, password, confirmation) },
                enabled = !state.busy && state.rateLimitRemainingSeconds == 0 && email.isNotBlank() && password.isNotBlank() && confirmation.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.busy) "Creando cuenta…" else "Registrarme")
            }

            TextButton(
                onClick = onBack,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("¿Ya tienes una cuenta? Iniciar sesión")
            }
        }
    }
}
