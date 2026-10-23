package com.petly_app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val MintBackground = Color(0xFFE5FFF4)
internal val MintContainer = Color(0xFFCEF5E7)
internal val Forest = Color(0xFF146E58)
internal val Ink = Color(0xFF062D24)
internal val WarmText = Color(0xFF5C5143)
internal val LoginType = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, color = Ink)

@Composable
internal fun AccessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Forest, onPrimary = Color.White,
        surface = Color.White, onSurface = Ink,
        background = MintBackground, onBackground = Ink,
        secondaryContainer = MintContainer, onSecondaryContainer = Ink
    )) { ProvideTextStyle(LoginType, content) }
}

@Composable
internal fun AccessHeader() {
    PetlyAccessLogo()
    Spacer(Modifier.height(16.dp))
    Text("Tu nuevo mejor amigo\nte está esperando", modifier = Modifier.fillMaxWidth(),
        style = LoginType.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Encuentra a tu compañero ideal", color = WarmText, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
}

@Composable
internal fun AccessTabs(creatingAccount: Boolean, busy: Boolean, onLogin: () -> Unit, onRegister: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MintContainer, CircleShape).padding(4.dp)
        .selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        for ((title, selected, action) in listOf(
            Triple("Iniciar sesión", !creatingAccount, onLogin),
            Triple("Crear cuenta", creatingAccount, onRegister)
        )) {
            if (selected) {
                Button(onClick = {}, enabled = !busy, modifier = Modifier.weight(1f)
                    .semantics { this.selected = true; role = Role.Tab }, shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                    Text(title, style = LoginType.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            } else {
                TextButton(onClick = action, enabled = !busy, modifier = Modifier.weight(1f)
                    .semantics { this.selected = false; role = Role.Tab },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(title, style = LoginType.copy(color = Forest, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
internal fun AccessField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    tag: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    transformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = label },
        enabled = enabled,
        singleLine = true,
        textStyle = LoginType.copy(fontSize = 15.sp, color = Ink),
        label = { Text(label, style = LoginType.copy(fontSize = 13.sp, color = WarmText)) },
        placeholder = { Text(placeholder, style = LoginType.copy(fontSize = 15.sp, color = Color(0xFF8A9A92))) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = transformation,
        leadingIcon = leading,
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color(0xFFF8FAF9),
            focusedBorderColor = Forest,
            unfocusedBorderColor = Color(0xFFBCCEC5),
            disabledBorderColor = Color(0xFFE0E8E4),
            focusedLabelColor = Forest,
            unfocusedLabelColor = WarmText,
            cursorColor = Forest
        )
    )
}
