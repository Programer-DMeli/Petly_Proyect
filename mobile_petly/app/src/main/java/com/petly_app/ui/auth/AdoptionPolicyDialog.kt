package com.petly_app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Displayed version of the policy included in this app release. */
internal const val ADOPTION_POLICY_VERSION = "2026-10-17"

@Composable
internal fun AdoptionPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Adopción responsable y bienestar animal") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Política de Petly · Versión $ADOPTION_POLICY_VERSION", fontWeight = FontWeight.Bold)
                Text("Al crear tu cuenta, te comprometes a tratar a los animales con respeto y a tomar decisiones de adopción que prioricen su bienestar durante toda su vida.")
                PolicySection("Un compromiso para toda la vida", "Antes de adoptar, considera el tiempo, el espacio y los recursos de tu hogar. Conversa con quienes viven contigo y brinda información verdadera al albergue para evaluar una convivencia adecuada.")
                PolicySection("Cuidado y un hogar seguro", "Ofrece agua limpia, alimentación adecuada, higiene, descanso, compañía y un entorno seguro. Respeta las necesidades de actividad y socialización de cada animal y busca atención veterinaria cuando la necesite.")
                PolicySection("Respeto y protección", "No abandones, maltrates ni utilices al animal para peleas, explotación o reproducción irresponsable. Educa con paciencia y sin violencia, y evita situaciones que pongan en riesgo al animal o a otras personas.")
                PolicySection("Una adopción acompañada", "Acuerda con el albergue las condiciones de entrega y seguimiento. Crear una cuenta en Petly no reserva una mascota ni garantiza una adopción; cada solicitud necesita su evaluación correspondiente.")
                PolicySection("Si no puedes continuar con el cuidado", "Contacta al albergue o a una organización de protección para buscar una solución responsable. No abandones al animal ni lo entregues a terceros sin coordinar su bienestar y las condiciones acordadas.")
                PolicySection("Reportar situaciones de riesgo", "Si detectas información falsa, maltrato o una publicación que pueda poner en peligro a un animal, comunícalo al albergue responsable y, ante una emergencia, a los servicios locales competentes.")
                Text("Aceptar esta política expresa tu compromiso con estos principios. Las condiciones específicas de cada adopción se acuerdan con el albergue antes de la entrega.")
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } })
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(body)
    }
}
