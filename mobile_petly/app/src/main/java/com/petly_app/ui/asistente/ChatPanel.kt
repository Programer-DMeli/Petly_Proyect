package com.petly_app.ui.asistente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.petly_app.ui.screen.HomeIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    state: ChatUiState,
    onOpen: (Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onEditFailed: () -> Unit,
    onClear: () -> Unit
) {
    if (!state.open) return
    val scroll = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.sending, state.error) {
        scroll.animateScrollToItem(state.messages.size + (if (state.sending || state.error != null) 1 else 0))
    }
    ModalBottomSheet(
        onDismissRequest = { onOpen(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFE8F8F5)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Asistente Petly", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Respuestas con IA · verifica los detalles con tu albergue", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onOpen(false) }) { Text("Cerrar") }
            }
            TextButton(onClick = onClear, enabled = state.messages.isNotEmpty() || state.draft.isNotEmpty()) {
                Text("Nueva conversación")
            }
            LazyColumn(
                state = scroll,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    ChatBubble("¡Hola! Soy el asistente de Petly. Puedo ayudarte con tu cuestionario, el proceso de adopción y cuidados básicos. ¿En qué paso estás?", false)
                }
                itemsIndexed(state.messages) { _, message -> ChatBubble(message.text, message.role == "user") }
                if (state.sending) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Petly está preparando una respuesta…")
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = onRetry) { Text("Reintentar") }
                            TextButton(onClick = onEditFailed) { Text("Editar mensaje") }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onEdit,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe tu consulta…") },
                    enabled = !state.sending && state.error == null,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    supportingText = { Text("${state.draft.length}/2000") }
                )
                Button(onClick = onSend, enabled = !state.sending && state.error == null && state.draft.isNotBlank()) {
                    Text("Enviar")
                }
            }
        }
    }
}

@Composable
fun ChatButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFF13614B), contentColor = Color.White,
        icon = { Icon(HomeIcons.CompanionHeart, contentDescription = null, modifier = Modifier.size(24.dp)) },
        text = { Text("Asistente") }
    )
}

@Composable
private fun ChatBubble(text: String, user: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 310.dp)
                .background(if (user) Color(0xFF13614B) else Color.White, RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Text(if (user) "Tú" else "Petly", color = if (user) Color.White else Color(0xFF13614B),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = if (user) Color.White else Color(0xFF0F3A2E))
        }
    }
}
