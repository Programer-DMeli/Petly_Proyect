package com.petly_app.ui.perfil

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
import androidx.compose.ui.draw.clip
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
import com.petly_app.data.repository.FavoritosManager
import com.petly_app.ui.auth.AccessIcons
import com.petly_app.ui.catalogo.FavoritosDialog
import com.petly_app.ui.common.PreviewNotice
import com.petly_app.ui.screen.HomeIcons
import com.petly_app.ui.screen.ScreenMintBackground

val ProfileDarkGreen = Color(0xFF13614B)
val ProfileTextDark = Color(0xFF0F3A2E)
val ProfileMintPill = Color(0xFFC8E8DA)
val ProfileWarmGold = Color(0xFFF7BD61)

@Composable
fun PerfilScreen(
    state: PerfilUiState,
    preview: Boolean,
    onOpenQuestionnaire: () -> Unit,
    onOpenDisponibilidad: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenKyc: () -> Unit = {},
    onDetailPet: (Long) -> Unit = {},
    onExit: () -> Unit = {}
) {
    val favorites by FavoritosManager.favorites.collectAsStateWithLifecycle()
    var showFavoritesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenMintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HEADER BAR
        PerfilHeaderBar(
            onOpenFavorites = { showFavoritesDialog = true },
            favoritesCount = favorites.size
        )

        if (preview) {
            PreviewNotice(preview = true)
        }

        // CARD 1: PERFIL HEADER CARD
        UserProfileHeaderCard()

        // CARD 2: CUESTIONARIO DE ADOPCIÓN BANNER
        AdoptionQuestionnaireBannerCard(onOpenQuestionnaire = onOpenQuestionnaire)

        OutlinedButton(onClick = onOpenKyc, modifier = Modifier.fillMaxWidth()) {
            Text("Verificación de identidad (DNI)")
        }

        // CARD 3: MENU OPTIONS CARD
        ProfileMenuOptionsCard(
            onOpenFavorites = { showFavoritesDialog = true },
            onOpenDisponibilidad = onOpenDisponibilidad,
            onOpenNotifications = onOpenNotifications,
            onExit = onExit
        )

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showFavoritesDialog) {
        FavoritosDialog(
            favorites = favorites,
            onDismiss = { showFavoritesDialog = false },
            onRemoveFavorite = { FavoritosManager.removeFavorite(it) },
            onDetailPet = onDetailPet
        )
    }
}

@Composable
private fun PerfilHeaderBar(
    onOpenFavorites: () -> Unit,
    favoritesCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo + Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ProfileDarkGreen,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AccessIcons.Paw,
                        contentDescription = "Petly logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Petly",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfileDarkGreen
                )
                Text(
                    text = "Perfil",
                    fontSize = 12.sp,
                    color = Color(0xFF2E6C58)
                )
            }
        }

        // Header Actions Right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BadgedBox(
                badge = {
                    if (favoritesCount > 0) {
                        Badge(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ) {
                            Text("$favoritesCount")
                        }
                    }
                }
            ) {
                IconButton(onClick = onOpenFavorites) {
                    Icon(
                        imageVector = if (favoritesCount > 0) HomeIcons.HeartFilled else HomeIcons.HeartOutlined,
                        contentDescription = "Mis Favoritos",
                        tint = if (favoritesCount > 0) Color(0xFFE53935) else ProfileDarkGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFFE4F5ED),
                modifier = Modifier.height(34.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Prof",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProfileDarkGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeaderCard() {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Avatar with Golden Camera Button
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=1000",
                    contentDescription = "Foto de perfil de Camila Torres",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                )

                Surface(
                    shape = CircleShape,
                    color = ProfileWarmGold,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = HomeIcons.Camera,
                            contentDescription = "Cambiar foto",
                            tint = Color(0xFF3B2404),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Name
            Text(
                text = "Camila Torres",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = ProfileTextDark
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Email
            Text(
                text = "camila.torres@refugiopetly.pe",
                fontSize = 13.sp,
                color = Color(0xFF6B7D73)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Adoptante Verificado Badge
            Surface(
                shape = CircleShape,
                color = ProfileMintPill
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = HomeIcons.Check,
                        contentDescription = null,
                        tint = ProfileDarkGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Adoptante verificado",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProfileDarkGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Metrics Box
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFEFF9F4),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricItem(value = "3", label = "Postulaciones")
                    MetricItem(value = "100%", label = "Perfil listo")
                    MetricItem(value = "1", label = "Visitas agendadas")
                }
            }
        }
    }
}

@Composable
private fun MetricItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ProfileDarkGreen
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4A6258)
        )
    }
}

@Composable
private fun AdoptionQuestionnaireBannerCard(onOpenQuestionnaire: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF8DE2BA),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = HomeIcons.Clipboard,
                            contentDescription = null,
                            tint = ProfileDarkGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuestionario de Adopción",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = ProfileTextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Completa tus datos de hogar y convivencia para evaluar compatibilidad con tu futura mascota.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF4A6258)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onOpenQuestionnaire,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ProfileDarkGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .semantics { contentDescription = "Mi cuestionario" }
            ) {
                Text(
                    text = "Ir al Cuestionario",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = HomeIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuOptionsCard(
    onOpenFavorites: () -> Unit,
    onOpenDisponibilidad: () -> Unit,
    onOpenNotifications: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Option 1: Mis Favoritos
            ProfileMenuItem(
                icon = HomeIcons.CompanionHeart,
                iconBg = Color(0xFF8DE2BA),
                iconTint = ProfileDarkGreen,
                title = "Mis Favoritos",
                titleColor = ProfileTextDark,
                onClick = onOpenFavorites
            )

            HorizontalDivider(color = Color(0xFFF0F5F2), thickness = 1.dp)

            // Option 2: Horarios de Disponibilidad (US-20)
            ProfileMenuItem(
                icon = HomeIcons.Clock,
                iconBg = Color(0xFF8DE2BA),
                iconTint = ProfileDarkGreen,
                title = "Horarios de Disponibilidad",
                titleColor = ProfileTextDark,
                onClick = onOpenDisponibilidad
            )

            HorizontalDivider(color = Color(0xFFF0F5F2), thickness = 1.dp)

            // Option 3: Notificaciones
            ProfileMenuItem(
                icon = HomeIcons.Bell,
                iconBg = Color(0xFF8DE2BA),
                iconTint = ProfileDarkGreen,
                title = "Notificaciones",
                titleColor = ProfileTextDark,
                onClick = onOpenNotifications
            )

            HorizontalDivider(color = Color(0xFFF0F5F2), thickness = 1.dp)

            // Option 4: Cerrar sesión
            ProfileMenuItem(
                icon = HomeIcons.Logout,
                iconBg = Color(0xFFFDE8E8),
                iconTint = Color(0xFFE53935),
                title = "Cerrar sesión",
                titleColor = Color(0xFFE53935),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    titleColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = titleColor
            )
        }

        Icon(
            imageVector = HomeIcons.ChevronRight,
            contentDescription = null,
            tint = titleColor.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PerfilScreenPreview() {
    PerfilScreen(
        state = PerfilUiState(),
        preview = false,
        onOpenQuestionnaire = {}
    )
}
