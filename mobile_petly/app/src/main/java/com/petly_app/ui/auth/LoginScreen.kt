package com.petly_app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MintBackground = Color(0xFFE5FFF4)
private val MintField = Color(0xFFD4FBED)
private val MintContainer = Color(0xFFCEF5E7)
private val Forest = Color(0xFF146E58)
private val Ink = Color(0xFF062D24)
private val WarmText = Color(0xFF5C5143)
private val LoginType = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, color = Ink)

/** Reference access screen. Authentication and cooldowns remain owned by AuthViewModel. */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onCheckConnection: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onRecovery: () -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Keep credentials in memory only; never put the password into saved instance state.
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var developerOptions by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val accountRemaining = if (state.blockedEmail == email.trim().lowercase()) state.lockRemainingSeconds else 0
    val remaining = maxOf(accountRemaining, state.rateLimitRemainingSeconds)
    val canSubmit = !state.busy && remaining == 0 && email.isNotBlank() && password.isNotBlank()
    val submit = {
        if (canSubmit) {
            focus.clearFocus()
            onLogin(email, password)
        }
    }
    // This screen has its own light palette to match the supplied artwork in either system mode.
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Forest, onPrimary = Color.White,
        surface = Color.White, onSurface = Ink,
        background = MintBackground, onBackground = Ink,
        secondaryContainer = MintContainer, onSecondaryContainer = Ink
    )) {
        ProvideTextStyle(LoginType) {
            Column(
                Modifier.fillMaxSize().background(MintBackground).imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PetlyAccessLogo()
                Spacer(Modifier.height(16.dp))
                Text("Tu nuevo mejor amigo\nte está esperando", modifier = Modifier.fillMaxWidth(),
                    style = LoginType.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Encuentra a tu compañero ideal", color = WarmText, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Surface(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(44.dp), color = Color.White,
                    shadowElevation = 5.dp
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                        Row(Modifier.fillMaxWidth().background(MintContainer, CircleShape).padding(4.dp)
                            .selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {}, modifier = Modifier.weight(1f)
                                .semantics { selected = true }, shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                                Text("Iniciar sesión", style = LoginType.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            TextButton(onClick = onRegister, enabled = !state.busy,
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Crear cuenta", style = LoginType.copy(color = Forest, fontWeight = FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Correo electrónico", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        AccessField(email, { email = it }, "ejemplo@correo.com", "Correo electrónico",
                            "login_email", !state.busy, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                        Spacer(Modifier.height(10.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.Center) {
                            Text("Contraseña", modifier = Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.Bold)
                            TextButton(onClick = onRecovery, enabled = !state.busy,
                                contentPadding = PaddingValues(start = 6.dp, end = 0.dp)) {
                                Text("¿Olvidaste tu contraseña?", style = LoginType.copy(fontSize = 12.sp, color = Forest))
                            }
                        }
                        AccessField(password, { password = it }, "Tu contraseña", "Contraseña",
                            "login_password", !state.busy,
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            KeyboardActions(onDone = { submit() }),
                            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.busy) {
                                    Icon(AccessIcons.Eye, if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                        tint = WarmText, modifier = Modifier.size(20.dp))
                                }
                            })
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                style = LoginType.copy(fontSize = 13.sp))
                        }
                        if (remaining > 0) {
                            val waitLabel = "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
                            Text((if (state.rateLimitRemainingSeconds > 0) "Límite de peticiones alcanzado" else "Cuenta bloqueada temporalmente") +
                                ". Puedes volver a intentar en $waitLabel.", color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 10.dp), style = LoginType.copy(fontSize = 13.sp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { submit() }, enabled = canSubmit, shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("login_submit"),
                            // Keep the reference's green CTA while empty; semantics still report disabled.
                            colors = ButtonDefaults.buttonColors(containerColor = Forest, disabledContainerColor = Forest,
                                disabledContentColor = Color.White)) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.busy) "Ingresando…" else "Entrar",
                                style = LoginType.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            Spacer(Modifier.width(10.dp))
                            Icon(AccessIcons.Arrow, null, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = MintContainer)
                            Text("O ACCEDE CON", modifier = Modifier.padding(horizontal = 12.dp),
                                style = LoginType.copy(fontSize = 10.sp, letterSpacing = 0.6.sp, color = WarmText))
                            HorizontalDivider(Modifier.weight(1f), color = MintContainer)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlternativeAccess("Google", Modifier.weight(1f), state.busy, false) {
                                notice = "El acceso con Google aún no está disponible. Por ahora, ingresa con tu correo y contraseña."
                            }
                            AlternativeAccess("Huella", Modifier.weight(1f), state.busy, true) {
                                notice = "El acceso con huella aún no está disponible. Por ahora, ingresa con tu correo y contraseña."
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.Center) {
                            Text("¿Aún no tienes cuenta?", color = WarmText, fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterVertically))
                            TextButton(onClick = onRegister, enabled = !state.busy,
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Regístrate gratis", style = LoginType.copy(fontSize = 12.sp, color = Forest, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
                if (onPreview != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { developerOptions = true }, enabled = !state.busy) {
                        Text("Opciones de desarrollo", style = LoginType.copy(fontSize = 12.sp, color = Forest))
                    }
                }
            }
            notice?.let { message ->
                AlertDialog(onDismissRequest = { notice = null }, title = { Text("Próximamente") },
                    text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("Entendido") } })
            }
            if (developerOptions) {
                AlertDialog(onDismissRequest = { developerOptions = false }, title = { Text("Opciones de desarrollo") },
                    text = {
                        Column {
                            Text(state.connectionMessage ?: "Comprueba la conexión con el servidor.")
                            TextButton(onClick = onCheckConnection, enabled = !state.checkingConnection && !state.busy) {
                                Text(if (state.checkingConnection) "Comprobando…" else "Comprobar conexión")
                            }
                            TextButton(onClick = { developerOptions = false; onPreview?.invoke() }) { Text("Ver pantallas de prueba") }
                            Text("Datos ficticios. No inicia sesión ni guarda información en el servidor.")
                        }
                    }, confirmButton = { TextButton(onClick = { developerOptions = false }) { Text("Cerrar") } })
            }
        }
    }
}

@Composable
private fun AccessField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, label: String,
    tag: String, enabled: Boolean, keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    BasicTextField(value, onValueChange, modifier = Modifier.fillMaxWidth().testTag(tag)
        .semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = LoginType.copy(fontSize = 16.sp), keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, visualTransformation = transformation,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Forest),
        decorationBox = { inner ->
            Row(Modifier.background(MintField, CircleShape).heightIn(min = 48.dp).padding(start = 15.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = LoginType.copy(fontSize = 16.sp, color = Color(0xFF718C80)))
                    inner()
                }
                trailing?.invoke()
            }
        })
}

@Composable
private fun AlternativeAccess(label: String, modifier: Modifier, busy: Boolean, fingerprint: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = !busy,
        contentPadding = PaddingValues(horizontal = 12.dp), shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MintContainer, contentColor = Ink)) {
        if (fingerprint) Icon(AccessIcons.Fingerprint, null, modifier = Modifier.size(20.dp), tint = Color(0xFF947435))
        else Text("G", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Forest, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = LoginType.copy(fontWeight = FontWeight.Bold))
    }
}

@Preview(showBackground = true, widthDp = 352, heightDp = 796)
@Composable
private fun LoginReferencePreview() {
    LoginScreen(AuthUiState(), { _, _ -> }, {}, {})
}
