package com.petly_app.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petly_app.data.remote.EstadoResena
import com.petly_app.data.remote.ResenasApi
import kotlinx.coroutines.CancellationException

@Composable
internal fun ResenaRegistrada(estado: EstadoResena) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Reseña registrada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        estado.calificacion?.takeIf { it in 1..5 }?.let { estrellas ->
            Text(
                "★".repeat(estrellas) + "☆".repeat(5 - estrellas),
                modifier = Modifier.semantics { contentDescription = "Tu calificación de $estrellas de 5 estrellas" },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun OpinionesAlbergue(
    albergueId: Long,
    api: ResenasApi,
    resenaRegistrada: EstadoResena? = null,
    onRegistrada: (EstadoResena) -> Unit,
) {
    var estado by remember(albergueId) { mutableStateOf<EstadoResena?>(null) }
    var intento by remember(albergueId) { mutableIntStateOf(0) }
    var error by remember(albergueId) { mutableStateOf(false) }
    var abierto by rememberSaveable(albergueId) { mutableStateOf(false) }

    LaunchedEffect(api, albergueId, intento) {
        error = false
        try {
            estado = api.estado(albergueId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            error = true
        }
    }

    val registrada = estado?.takeIf { it.registrada } ?: resenaRegistrada?.takeIf { it.registrada }
    when {
        registrada != null -> ResenaRegistrada(registrada)
        error -> TextButton(onClick = { intento++ }) { Text("Reintentar consulta de reseña") }
        estado == null -> Text("Consultando tu reseña…")
        else -> TextButton(onClick = { abierto = true }) { Text("Opiniones y reseñas") }
    }

    if (abierto && estado?.registrada == false && registrada == null) {
        ResenaAlbergueDialog(albergueId, api, onDismiss = { abierto = false }, onRegistrada = {
            abierto = false
            estado = it
            onRegistrada(it)
        })
    }
}
