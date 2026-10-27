package com.petly_app.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.petly_app.data.model.Mascota
import com.petly_app.data.repository.FavoritosManager
import com.petly_app.data.repository.SolicitudAdopcionManager
import com.petly_app.ui.common.PreviewNotice
import com.petly_app.ui.screen.HomeIcons

val DetailMintBackground = Color(0xFFE8F8F5)
val DetailDarkGreen = Color(0xFF126858)
val DetailTextDark = Color(0xFF0F3A2E)
val DetailMintPill = Color(0xFFE2F5EC)
val DetailAmberBadge = Color(0xFFFFECC8)

@Composable
fun DetalleMascotaScreen(
    state: DetalleMascotaUiState,
    preview: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onConsejos: () -> Unit = {}
) {
    val favorites by FavoritosManager.favorites.collectAsStateWithLifecycle()
    val favoritoError by FavoritosManager.error.collectAsStateWithLifecycle()
    val submittedRequests by SolicitudAdopcionManager.submitted.collectAsStateWithLifecycle()
    var showSolicitudForm by remember { mutableStateOf(false) }

    val isSolicitudEnviada = remember(state.mascota, submittedRequests) {
        state.mascota != null && (
            SolicitudAdopcionManager.isSolicitudEnviada(state.mascota.id) ||
            submittedRequests.any { it.mascotaId == state.mascota.id && it.finalizado }
        )
    }

    val mascotaActualizada = state.mascota

    if (showSolicitudForm && state.mascota != null) {
        SolicitudAdopcionScreen(
            mascotaId = state.mascota.id,
            mascotaNombre = state.mascota.nombre,
            onDismiss = { showSolicitudForm = false },
            onSubmittedSuccess = { showSolicitudForm = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DetailMintBackground)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (favoritoError.isNotBlank()) Text(favoritoError)
            // TOP NAVIGATION HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "❮  Volver al catálogo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailDarkGreen
                    )
                }

                mascotaActualizada?.let { pet ->
                    val isFav = favorites.any { it.id == pet.id }
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { FavoritosManager.toggleFavorite(pet) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFav) HomeIcons.HeartFilled else HomeIcons.HeartOutlined,
                                contentDescription = if (isFav) "Quitar de favoritos" else "Guardar en favoritos",
                                tint = if (isFav) Color(0xFFE53935) else DetailDarkGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (preview) {
                PreviewNotice(preview = true)
            }

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DetailDarkGreen)
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }
                else -> mascotaActualizada?.let { mascota ->
                    // 1. FOTO PRINCIPAL CON BADGE
                    PetPhotoCard(mascota = mascota)

                    // 2. HEADER CARD DE LA MASCOTA
                    PetHeaderInfoCard(mascota = mascota)
                    mascota.compatibilidad?.let { score ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(score.porcentaje?.let { "$it% compatible" } ?: "Información insuficiente", style = MaterialTheme.typography.titleMedium)
                                Text(score.mensaje)
                                Text("Cobertura: ${score.cobertura}% · ${score.versionReglas}")
                                score.criterios.forEach { criterion ->
                                    Text("${criterion.nombre}: peso ${criterion.peso}% · " + (criterion.aporte?.let { "aporte %.2f puntos".format(it) } ?: "Dato desconocido"))
                                    Text(criterion.explicacion)
                                }
                                Text("El total ponderado se redondea al entero más cercano. El albergue decide cada adopción.")
                                if (!preview) {
                                    OutlinedButton(onClick = onConsejos, enabled = !state.consultandoIA) { Text(if (state.consultandoIA) "Consultando…" else "Consultar o reintentar consejos Gemini") }
                                    state.consejos?.let { advice ->
                                        Text("Sugerencias complementarias · ${advice.estado}")
                                        Text(advice.explicacion)
                                        advice.recomendaciones.forEach { Text("• $it") }
                                    }
                                }
                            }
                        }
                    }

                    // 3. FICHA TÉCNICA
                    FichaTecnicaSection(mascota = mascota)

                    // 4. ALBERGUE RESPONSABLE
                    AlbergueResponsableCard(mascota = mascota)

                    // 5. BANNER PROCESO SEGURO
                    ProcesoSeguroBanner(ubicacion = mascota.ubicacion)

                    if (isSolicitudEnviada) TextButton(onClick = { showSolicitudForm = true }) { Text("Consultar solicitud y fotos del hogar") }
                    // 6. BARRA ACCIÓN INFERIOR
                    if (mascota.estado.equals("Disponible", true) || isSolicitudEnviada) BottomActionBar(
                        nombreMascota = mascota.nombre,
                        solicitudEnviada = isSolicitudEnviada,
                        onAdoptClick = { showSolicitudForm = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun PetPhotoCard(mascota: Mascota) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imageUrl = mascota.fotografiaUrl?.takeIf { it.isNotBlank() }
                ?: getFallbackImageUrl(mascota.nombre, mascota.especie)

            AsyncImage(
                model = imageUrl,
                contentDescription = "Fotografía de ${mascota.nombre}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            val (badgeBg, badgeText) = when (mascota.estado.lowercase()) {
                "en proceso" -> Color(0xFF2E6C58) to "En proceso de adopción"
                "adoptado" -> Color(0xFFF7BD61) to "Adoptado"
                else -> DetailDarkGreen to "Disponible para adopción"
            }

            // Badge en foto superior izquierda
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = badgeBg,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF8DE2BA), CircleShape)
                    )
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PetHeaderInfoCard(mascota: Mascota) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "PERFIL VERIFICADO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = DetailDarkGreen
                    )
                    Icon(
                        imageVector = HomeIcons.Check,
                        contentDescription = "Verificado",
                        tint = DetailDarkGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }

                val ageLabel = mascota.edadMeses?.let {
                    val anos = it / 12
                    val meses = it % 12
                    "$anos año${if (anos != 1) "s" else ""} · $meses meses"
                } ?: "Edad por evaluar"

                Surface(
                    shape = CircleShape,
                    color = DetailMintPill
                ) {
                    Text(
                        text = ageLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailDarkGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = mascota.nombre,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = DetailTextDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Box de Cita / Descripción
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF4F9F6),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "“",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailDarkGreen,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = mascota.descripcion.takeIf { it.isNotBlank() }
                            ?: "${mascota.nombre} es muy juguetón y busca un hogar amoroso con compañía de todos los días.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF4A6258)
                    )
                }
            }
        }
    }
}

@Composable
private fun FichaTecnicaSection(mascota: Mascota) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "FICHA TÉCNICA",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = DetailDarkGreen
        )

        val formattedAge = mascota.edadMeses?.let {
            val anos = it / 12
            val meses = it % 12
            "$anos año $meses meses"
        } ?: "Por evaluar"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FichaCard(
                icon = HomeIcons.Resize,
                iconBg = DetailMintPill,
                iconTint = DetailDarkGreen,
                label = "Tamaño",
                value = mascota.tamano,
                modifier = Modifier.weight(1f)
            )

            FichaCard(
                icon = HomeIcons.Calendar,
                iconBg = DetailMintPill,
                iconTint = DetailDarkGreen,
                label = "Edad",
                value = formattedAge,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FichaCard(
                icon = HomeIcons.Smile,
                iconBg = DetailMintPill,
                iconTint = DetailDarkGreen,
                label = "Temperamento",
                value = mascota.temperamento,
                modifier = Modifier.weight(1f)
            )

            FichaCard(
                icon = HomeIcons.Bolt,
                iconBg = DetailAmberBadge,
                iconTint = Color(0xFF8A5A00),
                label = "Nivel de Energía",
                value = mascota.energia,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FichaCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7D73)
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DetailTextDark
                )
            }
        }
    }
}

@Composable
private fun AlbergueResponsableCard(mascota: Mascota) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "ALBERGUE RESPONSABLE",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = DetailDarkGreen
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = DetailMintPill,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HomeIcons.Leaf,
                                contentDescription = null,
                                tint = DetailDarkGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = mascota.albergue.takeIf { it.isNotBlank() } ?: "Refugio Verde Petly",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DetailTextDark
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = HomeIcons.LocationPin,
                                contentDescription = null,
                                tint = Color(0xFF6B7D73),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = mascota.ubicacion.takeIf { it.isNotBlank() } ?: "Lima, Perú",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7D73)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = DetailMintPill,
                    modifier = Modifier.clickable { }
                ) {
                    Text(
                        text = "Contactar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailDarkGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcesoSeguroBanner(ubicacion: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF9F4),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF8DE2BA),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HomeIcons.Check,
                        contentDescription = null,
                        tint = DetailDarkGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "Proceso Petly seguro: Desparasitado, vacunado y con seguimiento veterinario previo a la entrega en ${ubicacion.ifBlank { "Lima" }}.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFF4A6258)
            )
        }
    }
}

@Composable
private fun BottomActionBar(
    nombreMascota: String,
    solicitudEnviada: Boolean,
    onAdoptClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Share Button
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(52.dp)
                .clickable { }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = HomeIcons.Share,
                    contentDescription = "Compartir",
                    tint = DetailDarkGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (solicitudEnviada) {
            Surface(
                shape = CircleShape,
                color = DetailMintPill,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HomeIcons.Check,
                        contentDescription = null,
                        tint = DetailDarkGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Solicitud Enviada",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailDarkGreen
                    )
                }
            }
        } else {
            // CTA Button
            Button(
                onClick = onAdoptClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DetailDarkGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { contentDescription = "Quiero Adoptar a $nombreMascota" }
            ) {
                Text(
                    text = "Quiero Adoptar a $nombreMascota",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = HomeIcons.ArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun getFallbackImageUrl(nombre: String, especie: String): String {
    return when (nombre.lowercase()) {
        "domi" -> "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1000"
        "luna" -> "https://images.unsplash.com/photo-1552053831-71594a27632d?q=80&w=1000"
        "rocky" -> "https://images.unsplash.com/photo-1589941013453-ec89f33b5e95?q=80&w=1000"
        "milo" -> "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1000"
        "canela" -> "https://images.unsplash.com/photo-1543466835-00a7907e9de1?q=80&w=1000"
        "simba" -> "https://images.unsplash.com/photo-1573865526739-10659fec78a5?q=80&w=1000"
        "bella" -> "https://images.unsplash.com/photo-1534361960057-19889db9621e?q=80&w=1000"
        "thor" -> "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?q=80&w=1000"
        "nala" -> "https://images.unsplash.com/photo-1513245543132-31f507417b26?q=80&w=1000"
        else -> if (especie.lowercase().contains("gato")) "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1000"
        else "https://images.unsplash.com/photo-1543466835-00a7907e9de1?q=80&w=1000"
    }
}

@Preview(showBackground = true)
@Composable
private fun DetalleMascotaScreenPreview() {
    val sampleMascota = Mascota(
        id = 1L,
        nombre = "Domi",
        especie = "Gato",
        tamano = "Pequeño",
        edadMeses = 12,
        temperamento = "Tranquilo",
        energia = "Alta",
        descripcion = "Domi es muy juguetón y busca un hogar con compañía de todos los días.",
        albergue = "Refugio Verde Petly",
        ubicacion = "Lima, Perú",
        estado = "Disponible",
        fotografiaUrl = "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1000"
    )

    DetalleMascotaScreen(
        state = DetalleMascotaUiState(mascota = sampleMascota),
        preview = false,
        onRetry = {},
        onBack = {}
    )
}
