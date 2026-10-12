package com.petly_app.ui.auth
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun RecuperacionScreen(state: RecuperacionState, initialToken: String?, onRequest: (String) -> Unit,
    onConfirm: (String,String,String) -> Unit, onBack: () -> Unit) {
    var reset by remember { mutableStateOf(initialToken != null) }
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf(initialToken.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    LaunchedEffect(state.resetComplete) { if(state.resetComplete) { token="";password="";confirmation="" } }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Recuperar acceso", style = MaterialTheme.typography.headlineMedium)
        if(!state.resetComplete) {
            if(reset) {
                if(initialToken == null) OutlinedTextField(token,{ token=it },label = { Text("Código del enlace (token)") },enabled = !state.busy,modifier = Modifier.fillMaxWidth())
                PasswordField(password,{ password=it },"Nueva contraseña",!state.busy)
                PasswordField(confirmation,{ confirmation=it },"Repetir contraseña",!state.busy)
                Button(onClick = { onConfirm(token,password,confirmation) },enabled = !state.busy) { Text("Cambiar contraseña") }
            } else {
                Text("Recibirás un enlace por correo. Puedes abrirlo con Petly o copiar su código aquí.")
                OutlinedTextField(email,{ email=it },label = { Text("Correo electrónico") },enabled = !state.busy,modifier = Modifier.fillMaxWidth())
                Button(onClick = { onRequest(email) },enabled = !state.busy && email.isNotBlank()) { Text("Enviar enlace") }
                TextButton(onClick = { reset=true },enabled = !state.busy) { Text("Ya tengo un enlace") }
            }
        }
        if(state.busy) CircularProgressIndicator()
        state.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it) }
        TextButton(onClick = onBack,enabled = !state.busy) { Text("Volver al acceso") }
    }
}
