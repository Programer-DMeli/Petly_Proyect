package com.petly_app.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petly_app.core.network.apiErrorMessage
import com.petly_app.data.remote.CrearResena
import com.petly_app.data.remote.EstadoResena
import com.petly_app.data.remote.ResenasApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val MaxComentario = 1000

@Composable
internal fun ResenaAlbergueDialog(
    albergueId: Long,
    api: ResenasApi,
    onDismiss: () -> Unit,
    onRegistrada: (EstadoResena) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var calificacion by rememberSaveable(albergueId) { mutableIntStateOf(0) }
    var comentario by rememberSaveable(albergueId) { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!enviando) onDismiss() },
        title = { Text("Calificar y opinar") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("¿Cómo fue la atención? Elige de 1 a 5 estrellas.")
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (valor in 1..5) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = calificacion == valor,
                                    enabled = !enviando,
                                    role = Role.RadioButton,
                                    onClick = { calificacion = valor }
                                )
                                .semantics {
                                    contentDescription = if (valor == 1) "1 estrella" else "$valor estrellas"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (valor <= calificacion) "★" else "☆",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 28.sp
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comentario,
                    onValueChange = { if (it.length <= MaxComentario) comentario = it },
                    label = { Text("Tu opinión") },
                    supportingText = { Text("${comentario.length}/$MaxComentario") },
                    enabled = !enviando,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !enviando && calificacion in 1..5 && comentario.isNotBlank(), onClick = {
                if (!enviando) {
                    enviando = true
                    error = null
                    scope.launch {
                        try {
                            api.crear(albergueId, CrearResena(calificacion, comentario.trim()))
                            onRegistrada(EstadoResena(true, calificacion))
                        } catch (exception: Exception) {
                            if (exception is CancellationException) throw exception
                            when ((exception as? HttpException)?.code()) {
                                409 -> onRegistrada(EstadoResena(true, null))
                                403 -> error = "El albergue debe validar el cierre de tu adopción antes de que puedas reseñarlo."
                                else -> error = apiErrorMessage(exception)
                            }
                        } finally {
                            enviando = false
                        }
                    }
                }
            }) { Text("Publicar reseña") }
        },
        dismissButton = { TextButton(enabled = !enviando, onClick = onDismiss) { Text("Cancelar") } }
    )
}
