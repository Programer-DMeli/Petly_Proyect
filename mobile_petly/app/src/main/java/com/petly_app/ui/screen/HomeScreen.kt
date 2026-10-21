package com.petly_app.ui.screen

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.petly_app.data.repository.FavoritosManager
import com.petly_app.ui.auth.AccessIcons
import com.petly_app.ui.catalogo.FavoritosDialog

val ScreenMintBackground = Color(0xFFEAFAF1)
val PetlyDarkGreen = Color(0xFF13614B)
val PetlyTextDark = Color(0xFF0F3A2E)
val MintAccentPill = Color(0xFF8DE2BA)
val WarmButtonGold = Color(0xFFF7BD61)

@Composable
fun HomeScreen(
    onNavigateCatalog: (String?) -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onNavigateDetail: (Long) -> Unit = {}
) {
    val favorites by FavoritosManager.favorites.collectAsStateWithLifecycle()
    var showFavoritesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenMintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // TOP HEADER BAR
        HomeHeaderBar(
            onNavigateProfile = onNavigateProfile,
            onOpenFavorites = { showFavoritesDialog = true },
            favoritesCount = favorites.size
        )

        // GREETING SECTION
        GreetingSection()

        // HERO BANNER CARD ("Adopta amor")
        AdoptaAmorCard(onVerMascotas = { onNavigateCatalog(null) })

        // CATEGORY SELECTION ("¿A quién buscas?")
        CategorySelectionSection(onCategorySelect = { especie -> onNavigateCatalog(especie) })

        // HEROES LOCALES / ALBERGUE DEL MES
        AlbergueDelMesCard(onVerAlbergue = { onNavigateCatalog(null) })

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showFavoritesDialog) {
        FavoritosDialog(
            favorites = favorites,
            onDismiss = { showFavoritesDialog = false },
            onRemoveFavorite = { FavoritosManager.removeFavorite(it) },
            onDetailPet = onNavigateDetail
        )
    }
}

@Composable
private fun HomeHeaderBar(
    onNavigateProfile: () -> Unit,
    onOpenFavorites: () -> Unit,
    favoritesCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App logo & name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = PetlyDarkGreen,
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
                    color = PetlyDarkGreen
                )
                Text(
                    text = "Inicio",
                    fontSize = 12.sp,
                    color = Color(0xFF2E6C58)
                )
            }
        }

        // Action icons on top right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        contentDescription = "Favoritos",
                        tint = if (favoritesCount > 0) Color(0xFFE53935) else PetlyDarkGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Profile badge / avatar
            Surface(
                shape = CircleShape,
                color = Color(0xFFE4F5ED),
                modifier = Modifier
                    .height(34.dp)
                    .clickable(onClickLabel = "Mi cuestionario", onClick = onNavigateProfile)
                    .semantics { contentDescription = "Mi cuestionario" }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Prof",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PetlyDarkGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "COMUNIDAD PETLY",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = PetlyDarkGreen
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "¡Hola! 👋",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PetlyTextDark
            )
        }
        Surface(
            shape = CircleShape,
            color = MintAccentPill,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = HomeIcons.CompanionHeart,
                    contentDescription = "Comunidad Petly",
                    tint = PetlyDarkGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AdoptaAmorCard(onVerMascotas: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1534361960057-19889db9621e?q=80&w=1000",
                contentDescription = "Adopta amor - Joven abrazando a su perro",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            ),
                            startY = 60f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Adopta amor",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Encuentra hoy a tu mejor amigo.",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onVerMascotas,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = WarmButtonGold),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Ver mascotas",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B2404)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = AccessIcons.Paw,
                        contentDescription = null,
                        tint = Color(0xFF3B2404),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelectionSection(onCategorySelect: (String) -> Unit) {
    Column {
        Text(
            text = "¿A quién buscas?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PetlyTextDark
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Perros Card
            Surface(
                onClick = { onCategorySelect("Perro") },
                shape = RoundedCornerShape(28.dp),
                color = PetlyDarkGreen,
                modifier = Modifier
                    .weight(1f)
                    .height(125.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = HomeIcons.Dog,
                        contentDescription = "Perros",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Perros",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Gatos Card
            Surface(
                onClick = { onCategorySelect("Gato") },
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(125.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = HomeIcons.Cat,
                        contentDescription = "Gatos",
                        tint = PetlyDarkGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gatos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PetlyDarkGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbergueDelMesCard(onVerAlbergue: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HÉROES LOCALES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = PetlyDarkGreen
                )
                Text(
                    text = "Albergue del mes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PetlyTextDark
                )
            }
            Surface(
                shape = CircleShape,
                color = MintAccentPill
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = HomeIcons.Check,
                        contentDescription = "Verificado",
                        tint = PetlyTextDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Verificado",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PetlyTextDark
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1548767797-d8c844163c4c?q=80&w=1000",
                        contentDescription = "Fotografía de Refugio Patitas Felices",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC1F3A2E),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = HomeIcons.LocationPin,
                                contentDescription = "Ubicación",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Guadalajara",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Refugio Patitas Felices",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PetlyTextDark
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onVerAlbergue,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PetlyDarkGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = AccessIcons.Paw,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ver albergue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
