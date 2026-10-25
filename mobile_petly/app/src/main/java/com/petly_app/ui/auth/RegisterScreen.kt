package com.petly_app.ui.auth

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (String, String, String, String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var policyOpen by rememberSaveable { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val errors = buildMap {
        if (name.trim().length !in 2..50) put("name", "Escribe tu nombre completo, entre 2 y 50 caracteres.")
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || email.trim().length > 100)
            put("email", "Ingresa un correo válido, por ejemplo camila@adopta.org.")
        if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72)
            put("password", "Usa al menos 8 caracteres y hasta 72 bytes UTF-8.")
        if (confirmation != password) put("confirmation", "Las contraseñas no coinciden.")
    }
    val canSubmit = !state.busy && !state.registered && state.rateLimitRemainingSeconds == 0 && accepted &&
        name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && confirmation.isNotBlank()
    val submit = {
        if (canSubmit) {
            attempted = true
            if (errors.isEmpty()) {
                focus.clearFocus()
                onRegister(name.trim(), email.trim(), password, confirmation, accepted)
            }
        }
    }
    BackHandler(enabled = state.busy) { /* Prevent leaving during account creation. */ }
    LaunchedEffect(state.registered) {
        if (state.registered) { password = ""; confirmation = ""; focus.clearFocus() }
    }

    AccessTheme {
        Column(Modifier.fillMaxSize().background(MintBackground).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AccessHeader()
            Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp),
                color = Color.White, shadowElevation = 5.dp) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                    AccessTabs(creatingAccount = true, busy = state.busy, onLogin = onBack, onRegister = {})
                    Spacer(Modifier.height(20.dp))
                    if (state.registered) {
                        Icon(AccessIcons.Check, null, tint = Forest, modifier = Modifier.size(36.dp).align(Alignment.CenterHorizontally))
                        Text("¡Cuenta creada con éxito!", color = Forest, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                        Text("Tu perfil Petly ya está listo. Inicia sesión con tu correo y contraseña para comenzar.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Text("Iniciar sesión") }
                    } else {
                        AccessField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "Ej. Camila Morales",
                            label = "Nombre completo",
                            tag = "register_name",
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            leading = { Icon(AccessIcons.Person, null, tint = Forest, modifier = Modifier.size(18.dp)) }
                        )
                        RegistrationError(if (attempted) errors["name"] else null)
                        Spacer(Modifier.height(10.dp))

                        AccessField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "camila@adopta.org",
                            label = "Correo electrónico",
                            tag = "register_email",
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            leading = { Icon(AccessIcons.Mail, null, tint = Forest, modifier = Modifier.size(18.dp)) }
                        )
                        RegistrationError(if (attempted) errors["email"] else null)
                        Spacer(Modifier.height(10.dp))

                        RegistrationPassword(
                            value = password,
                            onChange = { password = it },
                            placeholder = "Mínimo 8 caracteres",
                            label = "Crea tu contraseña",
                            tag = "register_password",
                            enabled = !state.busy,
                            visible = passwordVisible,
                            onToggle = { passwordVisible = !passwordVisible },
                            imeAction = ImeAction.Next,
                            leadingIcon = AccessIcons.Lock
                        )
                        RegistrationError(if (attempted) errors["password"] else null)
                        Spacer(Modifier.height(10.dp))

                        RegistrationPassword(
                            value = confirmation,
                            onChange = { confirmation = it },
                            placeholder = "Repite tu contraseña",
                            label = "Confirmar contraseña",
                            tag = "register_confirmation",
                            enabled = !state.busy,
                            visible = confirmationVisible,
                            onToggle = { confirmationVisible = !confirmationVisible },
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            leadingIcon = AccessIcons.Check
                        )
                        RegistrationError(if (attempted) errors["confirmation"] else null)
                        Spacer(Modifier.height(12.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it }, enabled = !state.busy,
                                modifier = Modifier.testTag("register_terms").semantics {
                                    contentDescription = "Acepto los términos de adopción responsable y la política de bienestar animal"
                                }, colors = CheckboxDefaults.colors(checkedColor = Forest))
                            Text(buildAnnotatedString {
                                append("Acepto los ")
                                withLink(LinkAnnotation.Clickable("adoption_policy",
                                    styles = TextLinkStyles(SpanStyle(color = Forest, textDecoration = TextDecoration.Underline)),
                                    linkInteractionListener = { policyOpen = true })) {
                                    append("Términos de Adopción Responsable")
                                }
                                append(" y la política de bienestar animal.")
                            }, style = LoginType.copy(fontSize = 12.sp, lineHeight = 16.sp, color = WarmText),
                                modifier = Modifier.weight(1f).testTag("register_policy"))
                        }
                        state.error?.let { RegistrationError(it) }
                        if (state.rateLimitRemainingSeconds > 0) RegistrationError(
                            "Límite de peticiones. Vuelve a intentar en ${state.rateLimitRemainingSeconds} segundos.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { submit() }, enabled = canSubmit, shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register_submit"),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBC5E), contentColor = Color(0xFF744B0A),
                                disabledContainerColor = Color(0xFFFFD18D), disabledContentColor = Color(0xFF775C31))) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF744B0A), strokeWidth = 2.dp)
                            else Icon(AccessIcons.Paw, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.busy) "Creando cuenta…" else "Crear mi perfil Petly",
                                style = LoginType.copy(fontWeight = FontWeight.Bold, color = Color(0xFF744B0A)))
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("¿Ya tienes una cuenta?", style = LoginType.copy(fontSize = 12.sp, color = WarmText),
                                modifier = Modifier.align(Alignment.CenterVertically))
                            TextButton(onClick = onBack, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Inicia sesión", style = LoginType.copy(fontSize = 12.sp, color = Forest))
                            }
                        }
                    }
                }
            }
        }
        if (policyOpen) AdoptionPolicyDialog(onDismiss = { policyOpen = false })
    }
}

@Composable
private fun RegistrationError(error: String?) {
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = LoginType.copy(fontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun RegistrationPassword(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    label: String,
    tag: String,
    enabled: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector
) {
    AccessField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        label = label,
        tag = tag,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        leading = { Icon(leadingIcon, null, tint = Forest, modifier = Modifier.size(18.dp)) },
        trailing = {
            IconButton(onClick = onToggle, enabled = enabled) {
                Icon(AccessIcons.Eye, if (visible) "Ocultar $label" else "Mostrar $label", tint = WarmText, modifier = Modifier.size(20.dp))
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 352, heightDp = 796)
@Composable
private fun RegisterReferencePreview() { RegisterScreen(AuthUiState(), { _, _, _, _, _ -> }, {}) }
