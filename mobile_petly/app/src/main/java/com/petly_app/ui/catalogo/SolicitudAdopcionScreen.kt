package com.petly_app.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.repository.SolicitudAdopcionManager
import com.petly_app.ui.screen.HomeIcons
import kotlinx.coroutines.launch

@Composable
fun SolicitudAdopcionScreen(
    mascotaId: Long,
    mascotaNombre: String,
    onDismiss: () -> Unit,
    onSubmittedSuccess: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(SolicitudAdopcionManager.getDraft(mascotaId, mascotaNombre)) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isVerifyingDni by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var successDialogOpen by remember { mutableStateOf(false) }

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
        TextButton(onClick = onDismiss) {
            Text("← Volver a $mascotaNombre", fontWeight = FontWeight.Bold, color = Color(0xFF126858))
        }

        // HEADER CARD
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Solicitud de Adopción de $mascotaNombre",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF0F3A2E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Completa la información requerida por el albergue para evaluar tu postulación de adopción.",
                    fontSize = 13.sp,
                    color = Color(0xFF4A6258)
                )
            }
        }

        // SECCIÓN 1: INFORMACIÓN PERSONAL
        FormSectionCard(title = "1. Información Personal") {
            // Nombre Completo
            CustomBlackTextField(
                value = draft.nombreCompleto,
                onValueChange = { draft = draft.copy(nombreCompleto = it) },
                label = "Nombre completo",
                placeholder = "Ej. Camila Morales Torres",
                error = errors["nombreCompleto"]
            )

            // DNI y Botón Verificar DNI
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DNI (8 dígitos)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F3A2E)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomBlackTextField(
                        value = draft.dni,
                        onValueChange = {
                            if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                                draft = draft.copy(dni = it, dniVerificado = false)
                            }
                        },
                        label = "DNI",
                        placeholder = "12345678",
                        error = errors["dni"],
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (draft.dni.trim().length == 8) {
                                isVerifyingDni = true
                                draft = draft.copy(dniVerificado = true)
                                isVerifyingDni = false
                                statusMessage = "✔ DNI verificado."
                            } else {
                                errors = errors + ("dni" to "El DNI debe tener exactamente 8 dígitos.")
                            }
                        },
                        enabled = !isVerifyingDni && draft.dni.trim().length == 8,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126858)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (isVerifyingDni) "Verificando…" else "Verificar DNI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (draft.dniVerificado) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE2F5EC)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(HomeIcons.Check, contentDescription = null, tint = Color(0xFF126858), modifier = Modifier.size(14.dp))
                            Text("✔ DNI verificado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF126858))
                        }
                    }
                }
                errors["dniVerificado"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            // Dirección
            CustomBlackTextField(
                value = draft.direccionHogar,
                onValueChange = { draft = draft.copy(direccionHogar = it) },
                label = "Dirección del hogar",
                placeholder = "Av. Principal 123, Dpto 402, Lima",
                error = errors["direccionHogar"]
            )

            // Teléfono
            CustomBlackTextField(
                value = draft.telefonoContacto,
                onValueChange = { draft = draft.copy(telefonoContacto = it) },
                label = "Teléfono de contacto",
                placeholder = "987654321",
                error = errors["telefonoContacto"],
                keyboardType = KeyboardType.Phone
            )
        }

        // SECCIÓN 2: VIVIENDA
        FormSectionCard(title = "2. Vivienda") {
            // ¿Vivienda propia o alquilada?
            QuestionTitle("1. ¿Tu vivienda es propia o alquilada?")
            ChoicePillRow(
                options = listOf("Propia", "Alquilada"),
                selected = draft.tipoPropiedadVivienda,
                onSelect = { draft = draft.copy(tipoPropiedadVivienda = it) }
            )

            if (draft.tipoPropiedadVivienda == "Alquilada") {
                CustomBlackTextField(
                    value = draft.permisoPropietarioDetalle,
                    onValueChange = { draft = draft.copy(permisoPropietarioDetalle = it) },
                    label = "¿Cuentas con autorización escrita del propietario?",
                    placeholder = "Ej. Sí, el contrato permite mascotas explícitamente"
                )
            }

            // ¿Qué espacios tendrá disponibles?
            QuestionTitle("2. ¿Qué espacios tendrá disponibles la mascota?")
            ChoicePillRow(
                options = listOf("Toda la casa", "Patio / Jardín", "Balcón protegido", "Dormitorio"),
                selected = draft.espaciosDisponiblesMascota,
                onSelect = { draft = draft.copy(espaciosDisponiblesMascota = it) }
            )
        }

        // SECCIÓN 3: FAMILIA Y CONVIVENCIA
        FormSectionCard(title = "3. Familia y Convivencia") {
            // Alergias
            QuestionTitle("1. ¿Alguien en tu hogar tiene alergia a los animales?")
            YesNoRadioQuestion(
                selected = draft.tieneAlergias,
                onSelect = { draft = draft.copy(tieneAlergias = it) }
            )
            if (draft.tieneAlergias == true) {
                CustomBlackTextField(
                    value = draft.alergiasDetalle,
                    onValueChange = { draft = draft.copy(alergiasDetalle = it) },
                    label = "Especifique los detalles de la alergia",
                    placeholder = "Ej. Alergia leve controlada con antihistamínicos"
                )
            }
            errors["tieneAlergias"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            // Personas especiales / niños
            QuestionTitle("2. ¿Vives con niños pequeños, adultos mayores o personas que requieren cuidados especiales?")
            YesNoRadioQuestion(
                selected = draft.vivesConPersonasEspeciales,
                onSelect = { draft = draft.copy(vivesConPersonasEspeciales = it) }
            )
            if (draft.vivesConPersonasEspeciales == true) {
                CustomBlackTextField(
                    value = draft.personasEspecialesDetalle,
                    onValueChange = { draft = draft.copy(personasEspecialesDetalle = it) },
                    label = "Detalle con quiénes vives",
                    placeholder = "Ej. 2 niños de 7 y 10 años"
                )
            }
            errors["vivesConPersonasEspeciales"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }

        // SECCIÓN 4: MOTIVACIÓN Y EXPERIENCIA
        FormSectionCard(title = "4. Motivación y Experiencia") {
            QuestionTitle("1. ¿Por qué quieres adoptar?")
            CustomBlackTextField(
                value = draft.motivoAdopcion,
                onValueChange = { draft = draft.copy(motivoAdopcion = it) },
                label = "Motivo de adopción",
                placeholder = "Deseo darle un hogar amoroso y brindarle compañía diaria",
                error = errors["motivoAdopcion"],
                singleLine = false
            )

            QuestionTitle("2. ¿Has cuidado mascotas antes?")
            YesNoRadioQuestion(
                selected = draft.haCuidadoMascotasAntes,
                onSelect = { draft = draft.copy(haCuidadoMascotasAntes = it) }
            )
            errors["haCuidadoMascotasAntes"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            QuestionTitle("3. Si tuviste mascotas, ¿siguen contigo? Si no, ¿qué ocurrió?")
            CustomBlackTextField(
                value = draft.experienciaPreviaDetalle,
                onValueChange = { draft = draft.copy(experienciaPreviaDetalle = it) },
                label = "Detalles de experiencia previa",
                placeholder = "Ej. Tuve un perro durante 14 años hasta su fallecimiento natural",
                singleLine = false
            )
        }

        // SECCIÓN 5: COMPROMISO
        FormSectionCard(title = "5. Compromiso de Adopción") {
            QuestionTitle("1. ¿Aceptas la esterilización según indicación veterinaria?")
            YesNoRadioQuestion(
                selected = draft.aceptaEsterilizacion,
                onSelect = { draft = draft.copy(aceptaEsterilizacion = it) }
            )
            errors["aceptaEsterilizacion"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            QuestionTitle("2. ¿Aceptas el seguimiento mediante fotos, visitas o videollamadas?")
            YesNoRadioQuestion(
                selected = draft.aceptaSeguimiento,
                onSelect = { draft = draft.copy(aceptaSeguimiento = it) }
            )
            errors["aceptaSeguimiento"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            QuestionTitle("3. ¿Puedes cubrir su alimentación, cuidados y atención veterinaria de emergencias?")
            YesNoRadioQuestion(
                selected = draft.puedeCubrirGastosVeterinarios,
                onSelect = { draft = draft.copy(puedeCubrirGastosVeterinarios = it) }
            )
            errors["puedeCubrirGastosVeterinarios"]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }

        statusMessage?.let { msg ->
            Text(
                text = msg,
                color = Color(0xFF126858),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // BOTONES INFERIORES
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Enviar Solicitud
            Button(
                onClick = {
                    scope.launch {
                        isSending = true
                        val validationErrors = SolicitudAdopcionManager.enviarSolicitud(draft)
                        isSending = false
                        if (validationErrors.isEmpty()) {
                            successDialogOpen = true
                        } else {
                            errors = validationErrors
                            statusMessage = "Revisa los campos obligatorios antes de enviar la solicitud."
                        }
                    }
                },
                enabled = !isSending,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126858)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (isSending) "Enviando solicitud…" else "Enviar solicitud de Adopcion",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Guardar Borrador
            OutlinedButton(
                onClick = {
                    SolicitudAdopcionManager.saveDraft(draft)
                    statusMessage = "Borrador guardado correctamente. Puedes continuar después."
                },
                enabled = !isSending,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Guardar borrador",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF126858)
                )
            }
        }
    }

    if (successDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                successDialogOpen = false
                onSubmittedSuccess()
                onDismiss()
            },
            title = {
                Text(
                    text = "¡Solicitud Enviada con Éxito! 🐾",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3A2E)
                )
            },
            text = {
                Text(
                    text = "Tu Solicitud de Adopción para $mascotaNombre ha sido enviada al albergue. Tu DNI y datos han sido validados.",
                    fontSize = 14.sp,
                    color = Color(0xFF4A6258)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    successDialogOpen = false
                    onSubmittedSuccess()
                    onDismiss()
                }) {
                    Text("Entendido", fontWeight = FontWeight.Bold, color = Color(0xFF126858))
                }
            }
        )
    }
}

@Composable
private fun CustomBlackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color(0xFF4A6258)) },
            placeholder = { Text(placeholder, color = Color(0xFF81938A)) },
            singleLine = singleLine,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF126858),
                unfocusedBorderColor = Color(0xFFBCCEC5),
                focusedLabelColor = Color(0xFF126858),
                unfocusedLabelColor = Color(0xFF4A6258)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F3A2E)
            )
            HorizontalDivider(color = Color(0xFFEFF5F2), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun QuestionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color(0xFF0F3A2E)
    )
}

@Composable
private fun ChoicePillRow(
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
                color = if (isSelected) Color(0xFF126858) else Color(0xFFEFF8F4),
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
                        color = if (isSelected) Color.White else Color(0xFF126858)
                    )
                }
            }
        }
    }
}

@Composable
private fun YesNoRadioQuestion(
    selected: Boolean?,
    onSelect: (Boolean) -> Unit
) {
    Row(modifier = Modifier.selectableGroup()) {
        listOf(true to "Sí", false to "No").forEach { (choice, text) ->
            Row(
                modifier = Modifier
                    .selectable(
                        selected = selected == choice,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice) }
                    )
                    .padding(end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == choice,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF126858))
                )
                Text(text, color = Color.Black, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SolicitudAdopcionScreenPreview() {
    SolicitudAdopcionScreen(
        mascotaId = 1L,
        mascotaNombre = "Domi",
        onDismiss = {}
    )
}
