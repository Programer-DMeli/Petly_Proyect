package com.petly_app.ui.perfil

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petly_app.data.model.PerfilDraft
import com.petly_app.ui.common.PreviewNotice
import com.petly_app.ui.screen.HomeIcons

private val defaultHomePhotoSamples = listOf(
    "https://images.unsplash.com/photo-1583847268964-b28dc8f51f92?q=80&w=1000",
    "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?q=80&w=1000",
    "https://images.unsplash.com/photo-1541599540903-216a46ca1dc0?q=80&w=1000"
)

@Composable
fun CuestionarioScreen(
    state: PerfilUiState,
    disponibilidadState: DisponibilidadUiState = DisponibilidadUiState(),
    preview: Boolean,
    onEdit: (PerfilDraft) -> Unit,
    onSave: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDetailPet: (Long) -> Unit = {},
    onOpenKyc: () -> Unit = {},
    onAddFranja: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onRemoveFranja: (Long) -> Unit = {},
    onSaveDisponibilidad: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    var currentSubScreen by rememberSaveable { mutableStateOf("DASHBOARD") }

    when (currentSubScreen) {
        "DASHBOARD" -> {
            PerfilScreen(
                state = state,
                preview = preview,
                onOpenQuestionnaire = { currentSubScreen = "QUESTIONNAIRE" },
                onOpenDisponibilidad = { currentSubScreen = "DISPONIBILIDAD" },
                onOpenKyc = onOpenKyc,
                onDetailPet = onDetailPet,
                onExit = onExit
            )
        }
        "DISPONIBILIDAD" -> {
            DisponibilidadScreen(
                state = disponibilidadState,
                preview = preview,
                onAddFranja = onAddFranja,
                onRemoveFranja = onRemoveFranja,
                onSave = onSaveDisponibilidad,
                onRetry = onRetry,
                onBack = { currentSubScreen = "DASHBOARD" }
            )
        }
        else -> {
            CuestionarioFormScreen(
                state = state,
                preview = preview,
                onEdit = onEdit,
                onSave = onSave,
                onRetry = onRetry,
                onBack = { currentSubScreen = "DASHBOARD" },
                onShare = onShare
            )
        }
    }
}

@Composable
private fun CuestionarioFormScreen(
    state: PerfilUiState, preview: Boolean,
    onEdit: (PerfilDraft) -> Unit, onSave: (Boolean) -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit, onShare: (() -> Unit)? = null
) {
    var confirmExit by remember { mutableStateOf(false) }
    val busy = state.loading || state.saving
    val editable = !busy && state.loaded
    val requestBack = { if (state.dirty) confirmExit = true else onBack() }

    // Launcher para cargar fotos reales desde la galería (US-19)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val current = state.form.fotosViviendaUrls
            onEdit(state.form.copy(fotosViviendaUrls = current + it.toString()))
        }
    }

    // Cálculo del porcentaje de avance
    val form = state.form
    val questionsAnswered = listOf(
        form.vivienda.isNotBlank(),
        form.espacioExterior.isNotBlank(),
        form.horasFuera.isNotBlank(),
        form.tiempoPaseos.isNotBlank(),
        form.rutinaHabitual.isNotBlank(),
        form.ninosDetalle.isNotBlank() || form.tieneNinos != null,
        form.otrosAnimales.isNotBlank() || form.tieneOtrasMascotas != null,
        form.experienciaMascotas.isNotBlank(),
        form.cuidadosAsumibles.isNotEmpty(),
        form.presupuesto.isNotBlank()
    ).count { it }
    val progressFraction = (questionsAnswered.toFloat() / 10f).coerceIn(0.1f, 1f)

    BackHandler { if (!state.saving) requestBack() }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("¿Salir sin guardar?") },
        text = { Text("Los cambios no guardados se perderán.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFE8F8F5))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = requestBack, enabled = !state.saving) {
            Text("← Volver a mi perfil", fontWeight = FontWeight.Bold, color = Color(0xFF13614B))
        }

        PreviewNotice(preview)

        // HEADER & PROGRESS CARD
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
                                imageVector = HomeIcons.Clipboard,
                                contentDescription = null,
                                tint = Color(0xFF13614B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Mi Estilo de Vida y Convivencia",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF0F3A2E)
                        )
                        Text(
                            text = "Test de Compatibilidad de Adopción",
                            fontSize = 12.sp,
                            color = Color(0xFF13614B),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tus respuestas nos permitirán calcular el nivel de compatibilidad con las mascotas disponibles para encontrar tu compañero ideal.",
                    fontSize = 13.sp,
                    color = Color(0xFF4A6258),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progreso de Match",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF13614B)
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}% Match listo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF13614B)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = Color(0xFF13614B),
                    trackColor = Color(0xFFC8E8DA)
                )
            }
        }

        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            if (!state.dirty) OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Reintentar carga") }
        }

        // BLOQUE 1: ESPACIO E INSTALACIONES (P1, P2)
        FormCardSection(
            title = "1. Espacio e Instalaciones del Hogar",
            description = "Determina el espacio físico y seguridad disponible para la mascota."
        ) {
            // Pregunta 1
            QuestionItem(
                number = "1",
                question = "¿En qué tipo de vivienda vives?",
                purpose = "Compara necesidades de espacio"
            ) {
                SingleChoicePills(
                    options = listOf("Departamento", "Casa", "Otro"),
                    selected = form.vivienda,
                    onSelect = {
                        if (editable) onEdit(form.copy(vivienda = it))
                    }
                )
            }

            // Pregunta 2
            QuestionItem(
                number = "2",
                question = "¿Con qué espacio exterior seguro cuentas?",
                purpose = "Compara seguridad y necesidades de entorno"
            ) {
                SingleChoicePills(
                    options = listOf("Ninguno", "Patio amplio", "Jardín cercado", "Jardín sin cercar"),
                    selected = form.espacioExterior,
                    onSelect = {
                        if (editable) onEdit(form.copy(espacioExterior = it))
                    }
                )
            }
        }

        // BLOQUE 2: TIEMPO Y RUTINA (P3, P4, P5)
        FormCardSection(
            title = "2. Tiempo, Rutina y Nivel de Actividad",
            description = "Analiza el ritmo de vida y la presencia diaria en el hogar."
        ) {
            // Pregunta 3
            QuestionItem(
                number = "3",
                question = "¿Cuántas horas al día estaría sola la mascota?",
                purpose = "Indica las horas diarias reales para calcular la compatibilidad de rutina."
            ) {
                OutlinedTextField(
                    value = form.horasFuera,
                    onValueChange = { if (editable) onEdit(form.copy(horasFuera = it)) },
                    label = { Text("Horas fuera de casa (0 a 24)") },
                    enabled = editable,
                    isError = state.errors.containsKey("horasFuera"),
                    supportingText = { state.errors["horasFuera"]?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Pregunta 4
            QuestionItem(
                number = "4",
                question = "¿Cuánto tiempo diario puedes dedicar a paseos y juego?",
                purpose = "Compara nivel de energía y estimulación"
            ) {
                SingleChoicePills(
                    options = listOf("< 30 min", "30–60 min", "1–2 horas", "+ de 2 horas"),
                    selected = form.tiempoPaseos,
                    onSelect = {
                        if (editable) onEdit(form.copy(tiempoPaseos = it))
                    }
                )
            }

            // Pregunta 5
            QuestionItem(
                number = "5",
                question = "¿Cómo describes tu rutina habitual?",
                purpose = "Compara temperamento e intensidad"
            ) {
                SingleChoicePills(
                    options = listOf("Tranquila", "Moderadamente activa", "Muy activa"),
                    selected = form.rutinaHabitual,
                    onSelect = {
                        if (editable) onEdit(form.copy(rutinaHabitual = it))
                    }
                )
            }
        }

        // BLOQUE 3: CONVIVENCIA E INTEGRANTES (P6, P7)
        FormCardSection(
            title = "3. Convivencia e Integrantes del Hogar",
            description = "Garantiza una adaptación segura con niños y otras mascotas."
        ) {
            // Pregunta 6
            QuestionItem(
                number = "6",
                question = "¿Viven niños en tu hogar o te visitan frecuentemente?",
                purpose = "Compara compatibilidad conocida con niños"
            ) {
                SingleChoicePills(
                    options = listOf("No", "Menores de 6 años", "De 6 a 12 años", "Adolescentes"),
                    selected = form.ninosDetalle,
                    onSelect = {
                        if (editable) {
                            val esNo = it == "No"
                            onEdit(form.copy(ninosDetalle = it, tieneNinos = !esNo))
                        }
                    }
                )
            }

            // Pregunta 7
            QuestionItem(
                number = "7",
                question = "¿Qué otros animales viven contigo?",
                purpose = "Compara compatibilidad con otras mascotas"
            ) {
                SingleChoicePills(
                    options = listOf("Ninguno", "Perros", "Gatos", "Otros"),
                    selected = form.otrosAnimales,
                    onSelect = {
                        if (editable) {
                            val esNinguno = it == "Ninguno"
                            onEdit(form.copy(otrosAnimales = it, tieneOtrasMascotas = !esNinguno))
                        }
                    }
                )
            }
        }

        // BLOQUE 4: EXPERIENCIA Y CUIDADOS (P8, P9)
        FormCardSection(
            title = "4. Experiencia y Cuidados Especiales",
            description = "Evalúa la capacidad de asumir requerimientos específicos."
        ) {
            // Pregunta 8
            QuestionItem(
                number = "8",
                question = "¿Qué experiencia tienes cuidando mascotas?",
                purpose = "Compara nivel de manejo requerido"
            ) {
                SingleChoicePills(
                    options = listOf("Ninguna", "Básica", "Amplia (perros/gatos)"),
                    selected = form.experienciaMascotas,
                    onSelect = {
                        if (editable) onEdit(form.copy(experienciaMascotas = it))
                    }
                )
            }

            // Pregunta 9
            QuestionItem(
                number = "9",
                question = "¿Qué cuidados puedes asumir?",
                purpose = "Compara necesidades especiales de salud y conducta"
            ) {
                MultiChoicePills(
                    options = listOf("Medicación", "Cuidados adulto mayor", "Entrenamiento", "Adaptación conductual", "Ninguno adicional"),
                    selectedList = form.cuidadosAsumibles,
                    onToggleOption = { option ->
                        if (editable) {
                            val updated = if (form.cuidadosAsumibles.contains(option)) {
                                form.cuidadosAsumibles - option
                            } else {
                                form.cuidadosAsumibles + option
                            }
                            onEdit(form.copy(cuidadosAsumibles = updated))
                        }
                    }
                )
            }
        }

        // BLOQUE 5: PRESUPUESTO Y FOTOS DEL HOGAR (P10 & US-19)
        FormCardSection(
            title = "5. Presupuesto y Fotos de la Vivienda",
            description = "Verificación de entorno seguro y disponibilidad de recursos."
        ) {
            // Pregunta 10
            QuestionItem(
                number = "10",
                question = "¿Qué presupuesto mensual puedes destinar a su cuidado?",
                purpose = "Estimación de alimentación y atención médica"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.presupuesto,
                        onValueChange = { if (editable) onEdit(form.copy(presupuesto = it)) },
                        label = { Text("Monto aproximado en soles (S/)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF13614B),
                            focusedLabelColor = Color(0xFF13614B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SingleChoicePills(
                        options = listOf("100", "200", "350", "500"),
                        selected = form.presupuesto,
                        onSelect = { if (editable) onEdit(form.copy(presupuesto = it)) }
                    )
                }
            }

            // Fotografías de Vivienda (US-19)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Fotografías de tu hogar (US-19)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F3A2E)
                )
                Text(
                    text = "Sube fotos de la sala, patio o el área donde vivirá la mascota para verificar la seguridad del ambiente.",
                    fontSize = 12.sp,
                    color = Color(0xFF4A6258)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { if (editable) photoPickerLauncher.launch("image/*") },
                        enabled = editable,
                        shape = CircleShape
                    ) {
                        Icon(HomeIcons.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📷 Añadir foto", fontSize = 13.sp)
                    }

                    if (form.fotosViviendaUrls.isEmpty()) {
                        TextButton(
                            onClick = {
                                if (editable) onEdit(form.copy(fotosViviendaUrls = defaultHomePhotoSamples))
                            },
                            enabled = editable
                        ) {
                            Text("Usar muestras", fontSize = 12.sp, color = Color(0xFF13614B))
                        }
                    }
                }

                if (form.fotosViviendaUrls.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(form.fotosViviendaUrls) { index, photoUrl ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF4F9F6),
                                shadowElevation = 1.dp,
                                modifier = Modifier.width(160.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Foto ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                        )

                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(26.dp)
                                                .clickable {
                                                    if (editable) {
                                                        val updated = form.fotosViviendaUrls.toMutableList()
                                                        updated.removeAt(index)
                                                        onEdit(form.copy(fotosViviendaUrls = updated))
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // MATCH CARD PREVIEW
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF9F4)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF8DE2BA),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = HomeIcons.CompanionHeart,
                            contentDescription = null,
                            tint = Color(0xFF13614B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✨ Match de Compatibilidad Activo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F3A2E)
                    )
                    Text(
                        text = "Tus respuestas permiten filtrar mascotas cuyo temperamento y energía se adapten idealmente a tu hogar.",
                        fontSize = 12.sp,
                        color = Color(0xFF4A6258)
                    )
                }
            }
        }

        onShare?.let { TextButton(onClick = it, enabled = editable && !state.dirty) { Text("Compartir mi perfil con un albergue") } }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        // BOTONES DE ENVÍO
        Button(
            onClick = { onSave(true) },
            enabled = editable,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13614B)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { contentDescription = "Finalizar cuestionario" }
        ) {
            Text(
                if (state.saving) "Guardando…" else "Finalizar Cuestionario y Ver Matches 🐾",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        OutlinedButton(
            onClick = { onSave(false) },
            enabled = editable,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Guardar borrador" }
        ) {
            Text("Guardar borrador", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF13614B))
        }
    }
}

@Composable
private fun FormCardSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F3A2E)
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF4A6258)
            )
            HorizontalDivider(color = Color(0xFFEFF5F2), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun QuestionItem(
    number: String,
    question: String,
    purpose: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$number. $question",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color(0xFF0F3A2E)
        )
        Text(
            text = "💡 $purpose",
            fontSize = 11.sp,
            color = Color(0xFF2E6C58)
        )
        content()
    }
}

@Composable
private fun SingleChoicePills(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(options) { _, option ->
            val isSelected = selected.equals(option, ignoreCase = true)
            Surface(
                onClick = { onSelect(option) },
                shape = CircleShape,
                color = if (isSelected) Color(0xFF13614B) else Color(0xFFEFF8F4),
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF13614B)
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoicePills(
    options: List<String>,
    selectedList: List<String>,
    onToggleOption: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(options) { _, option ->
            val isSelected = selectedList.contains(option)
            Surface(
                onClick = { onToggleOption(option) },
                shape = CircleShape,
                color = if (isSelected) Color(0xFF13614B) else Color(0xFFEFF8F4),
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF13614B)
                    )
                }
            }
        }
    }
}
