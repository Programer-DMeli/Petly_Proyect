package com.petly_app.ui.perfil

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
    state: PerfilUiState, preview: Boolean,
    onEdit: (PerfilDraft) -> Unit, onSave: (Boolean) -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit, onShare: (() -> Unit)? = null,
    onDetailPet: (Long) -> Unit = {},
    onOpenKyc: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    var showForm by rememberSaveable { mutableStateOf(false) }

    if (!showForm) {
        PerfilScreen(
            state = state,
            preview = preview,
            onOpenQuestionnaire = { showForm = true },
            onOpenKyc = onOpenKyc,
            onDetailPet = onDetailPet,
            onExit = onExit
        )
    } else {
        CuestionarioFormScreen(
            state = state,
            preview = preview,
            onEdit = onEdit,
            onSave = onSave,
            onRetry = onRetry,
            onBack = { showForm = false },
            onShare = onShare
        )
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

    // Cálculo del porcentaje de avance del formulario
    val fieldsCompleted = listOf(
        state.form.vivienda.isNotBlank(),
        state.form.horasFuera.isNotBlank(),
        state.form.presupuesto.isNotBlank(),
        state.form.tieneNinos != null,
        state.form.tieneOtrasMascotas != null,
        state.form.fotosViviendaUrls.isNotEmpty()
    ).count { it }
    val progressFraction = fieldsCompleted.toFloat() / 6f

    BackHandler { if (!state.saving) requestBack() }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("¿Salir sin guardar?") },
        text = { Text("Los últimos cambios del formulario se perderán.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFE8F8F5))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = requestBack, enabled = !state.saving) {
            Text("← Volver a mi perfil", fontWeight = FontWeight.Bold, color = Color(0xFF13614B))
        }

        PreviewNotice(preview)

        // Título de sección y avance
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Formulario de Adopción Responsable",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF0F3A2E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Completa los datos de tu hogar y adjunta fotos de tu vivienda (US-19) para evaluar la compatibilidad con tu futura mascota.",
                    fontSize = 13.sp,
                    color = Color(0xFF4A6258)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Avance del cuestionario",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF13614B)
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}%",
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

        // SECCIÓN 1: ESTILO DE VIDA Y HOGAR
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
                    text = "1. Estilo de Vida y Hogar",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F3A2E)
                )

                FormField("Tipo de vivienda", state.form.vivienda, state.errors["vivienda"], editable) {
                    onEdit(state.form.copy(vivienda = it))
                }

                // Accesos rápidos de vivienda
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Casa", "Departamento", "Casa de campo").forEach { opcion ->
                        FilterChip(
                            selected = state.form.vivienda == opcion,
                            onClick = { if (editable) onEdit(state.form.copy(vivienda = opcion)) },
                            label = { Text(opcion, fontSize = 12.sp) }
                        )
                    }
                }

                FormField("Horas fuera de casa al día", state.form.horasFuera, state.errors["horasFuera"], editable, true) {
                    onEdit(state.form.copy(horasFuera = it))
                }

                FormField("Presupuesto mensual estimado (S/)", state.form.presupuesto, state.errors["presupuesto"], editable, true) {
                    onEdit(state.form.copy(presupuesto = it))
                }
            }
        }

        // SECCIÓN 2: CONVIVENCIA Y ENTORNO
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
                    text = "2. Convivencia y Entorno",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F3A2E)
                )

                BooleanQuestion("¿Viven niños en tu hogar?", state.form.tieneNinos, state.errors["ninos"], editable) {
                    onEdit(state.form.copy(tieneNinos = it))
                }

                BooleanQuestion("¿Tienes otras mascotas actualmente?", state.form.tieneOtrasMascotas, state.errors["mascotas"], editable) {
                    onEdit(state.form.copy(tieneOtrasMascotas = it))
                }
            }
        }

        // SECCIÓN 3: FOTOGRAFÍAS DE LA VIVIENDA (US-19)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF8DE2BA),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HomeIcons.Camera,
                                contentDescription = null,
                                tint = Color(0xFF13614B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "3. Fotos de la Vivienda (US-19)",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F3A2E)
                    )
                }

                Text(
                    text = "Muestra la sala, patio/jardín o el área de descanso donde vivirá la mascota para verificar la seguridad del espacio.",
                    fontSize = 13.sp,
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
                        Text("Añadir foto de tu hogar", fontSize = 13.sp)
                    }

                    if (state.form.fotosViviendaUrls.isEmpty()) {
                        TextButton(
                            onClick = {
                                if (editable) onEdit(state.form.copy(fotosViviendaUrls = defaultHomePhotoSamples))
                            },
                            enabled = editable
                        ) {
                            Text("Usar muestras", fontSize = 12.sp, color = Color(0xFF13614B))
                        }
                    }
                }

                val currentPhotos = if (state.form.fotosViviendaUrls.isNotEmpty()) {
                    state.form.fotosViviendaUrls
                } else {
                    emptyList()
                }

                if (currentPhotos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F8F5), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no has añadido fotografías de tu vivienda. Toca 'Añadir foto' para seleccionar de tu galería.",
                            fontSize = 12.sp,
                            color = Color(0xFF4A6258)
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(currentPhotos) { index, photoUrl ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF4F9F6),
                                shadowElevation = 1.dp,
                                modifier = Modifier.width(170.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                    ) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Foto de vivienda ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                        )

                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(28.dp)
                                                .clickable {
                                                    if (editable) {
                                                        val updated = currentPhotos.toMutableList()
                                                        updated.removeAt(index)
                                                        onEdit(state.form.copy(fotosViviendaUrls = updated))
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val etiqueta = when (index % 3) {
                                        0 -> "Sala / Área común"
                                        1 -> "Patio / Jardín"
                                        else -> "Lugar de descanso"
                                    }

                                    Text(
                                        text = etiqueta,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F3A2E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        onShare?.let { TextButton(onClick = it, enabled = editable && !state.dirty) { Text("Compartir mi perfil con un albergue") } }

        Text(
            text = if (state.form.finalizado) "Cuestionario finalizado" else "Puedes guardar respuestas incompletas como borrador.",
            fontSize = 13.sp,
            color = Color(0xFF4A6258)
        )

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Button(
            onClick = { onSave(true) },
            enabled = editable,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13614B)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (state.saving) "Guardando…" else "Finalizar y Enviar Cuestionario", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        OutlinedButton(
            onClick = { onSave(false) },
            enabled = editable,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Guardar borrador", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF13614B))
        }
    }
}

@Composable
private fun FormField(label: String, value: String, error: String?, enabled: Boolean, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF13614B),
            focusedLabelColor = Color(0xFF13614B)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BooleanQuestion(label: String, value: Boolean?, error: String?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF0F3A2E))
        Row(modifier = Modifier.selectableGroup()) {
            listOf(true to "Sí", false to "No").forEach { (choice, text) ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = value == choice,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onChange(choice) }
                        )
                        .semantics { contentDescription = "$label: $text" }
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = value == choice,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF13614B))
                    )
                    Text(text, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
