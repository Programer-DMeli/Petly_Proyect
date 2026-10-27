package com.petly_app.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petly_app.data.model.DisponibilidadValidation
import com.petly_app.data.model.FranjaHoraria
import com.petly_app.ui.common.PreviewNotice
import com.petly_app.ui.screen.HomeIcons

@Composable
fun DisponibilidadScreen(
    state: DisponibilidadUiState,
    preview: Boolean,
    onAddFranja: (String, String, String, String) -> Unit,
    onRemoveFranja: (Long) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var selectedDia by rememberSaveable { mutableStateOf("Lunes") }
    var horaInicio by rememberSaveable { mutableStateOf("10:00") }
    var horaFin by rememberSaveable { mutableStateOf("12:00") }
    var descripcion by rememberSaveable { mutableStateOf("") }

    val busy = state.loading || state.saving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFE8F8F5))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Volver
        TextButton(onClick = onBack, enabled = !busy) {
            Text("← Volver a mi perfil", fontWeight = FontWeight.Bold, color = Color(0xFF13614B))
        }

        PreviewNotice(preview)

        // HEADER & EXPLANATION CARD
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF8DE2BA),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HomeIcons.Clock,
                                contentDescription = null,
                                tint = Color(0xFF13614B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Horarios de Disponibilidad",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF0F3A2E)
                        )
                        Text(
                            text = "Zona Horaria: America/Lima (Perú) 🇵🇪",
                            fontSize = 12.sp,
                            color = Color(0xFF13614B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Configura tus franjas semanales para coordinar visitas domiciliarias o entrevistas con los albergues que cuenten con tu autorización explícita.",
                    fontSize = 13.sp,
                    color = Color(0xFF4A6258),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF9F4)
                ) {
                    Text(
                        text = "💡 Nota: Esta agenda semanal de visitas es independiente del campo de 'horas fuera de casa' del cuestionario general.",
                        fontSize = 12.sp,
                        color = Color(0xFF2E6C58),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF13614B))
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }

        // AGREGAR NUEVA FRANJA FORM CARD
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Añadir Franja Horaria Semanal",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F3A2E)
                )

                Text(
                    text = "Día de la semana:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3A2E)
                )

                // Selector de Día de la semana
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DisponibilidadValidation.diasSemana, key = { it }) { dia ->
                        val isSelected = selectedDia.equals(dia, ignoreCase = true)
                        Surface(
                            onClick = { selectedDia = dia },
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF13614B) else Color(0xFFEFF8F4),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dia,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF13614B)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = horaInicio,
                        onValueChange = { horaInicio = it },
                        label = { Text("Hora inicio") },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF13614B),
                            focusedLabelColor = Color(0xFF13614B)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = horaFin,
                        onValueChange = { horaFin = it },
                        label = { Text("Hora fin") },
                        placeholder = { Text("12:00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF13614B),
                            focusedLabelColor = Color(0xFF13614B)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Notas / Detalle opcional") },
                    placeholder = { Text("Ej. Entrevistas virtuales o visitas en casa") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF13614B),
                        focusedLabelColor = Color(0xFF13614B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Validation Error Alert
                state.validationError?.let { valErr ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFDE8E8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ $valErr",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        onAddFranja(selectedDia, horaInicio, horaFin, descripcion)
                    },
                    enabled = !busy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13614B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ Agregar Franja Horaria", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // FRANJAS REGISTRADAS LIST CARD
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Franjas Registradas (${state.franjas.size})",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F3A2E)
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFC8E8DA)
                    ) {
                        Text(
                            text = "${state.franjas.size} fijadas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF13614B),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (state.franjas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F8F5), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no has agregado franjas horarias. Completa el formulario superior para registrar tus horarios.",
                            fontSize = 13.sp,
                            color = Color(0xFF4A6258)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.franjas.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF4F9F6),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF13614B)
                                            ) {
                                                Text(
                                                    text = item.diaSemana,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = "${item.horaInicio} - ${item.horaFin}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F3A2E)
                                            )
                                        }

                                        if (item.descripcion.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.descripcion,
                                                fontSize = 12.sp,
                                                color = Color(0xFF4A6258)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { item.id?.let { onRemoveFranja(it) } }
                                    ) {
                                        Icon(
                                            imageVector = HomeIcons.Trash,
                                            contentDescription = "Eliminar franja",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.message?.let {
            Text(
                text = "✓ $it",
                color = Color(0xFF13614B),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // SAVE BUTTON
        Button(
            onClick = onSave,
            enabled = !busy,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13614B)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                if (state.saving) "Guardando..." else "Guardar Horarios de Disponibilidad (US-20)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DisponibilidadScreenPreview() {
    val sampleFranjas = listOf(
        FranjaHoraria(1L, "Lunes", "10:00", "13:00", "Disponible para entrevistas virtuales"),
        FranjaHoraria(2L, "Sábado", "15:00", "18:00", "Disponible para recibir visitas en casa")
    )
    DisponibilidadScreen(
        state = DisponibilidadUiState(franjas = sampleFranjas),
        preview = false,
        onAddFranja = { _, _, _, _ -> },
        onRemoveFranja = {},
        onSave = {},
        onRetry = {},
        onBack = {}
    )
}
