package com.petly_app.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.petly_app.data.model.CatalogoFilters
import com.petly_app.data.model.CatalogoOptions
import com.petly_app.data.model.Mascota
import com.petly_app.data.repository.FavoritosManager
import com.petly_app.ui.auth.AccessIcons
import com.petly_app.ui.common.PreviewNotice
import com.petly_app.ui.screen.HomeIcons

val CatalogMintBackground = Color(0xFFE8F8F5)
val CatalogDarkGreen = Color(0xFF13614B)
val CatalogTextDark = Color(0xFF0F3A2E)
val CatalogPillMint = Color(0xFFC8E8DA)
val CatalogAdoptedOrange = Color(0xFFF7BD61)

@Composable
fun CatalogoScreen(
    state: CatalogoUiState,
    preview: Boolean,
    onEdit: (CatalogoFilters) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onPage: (Int) -> Unit,
    onRetry: () -> Unit,
    onDetail: (Long) -> Unit,
    onProfile: () -> Unit,
    onExit: () -> Unit
) {
    val favorites by FavoritosManager.favorites.collectAsStateWithLifecycle()
    val favoritoError by FavoritosManager.error.collectAsStateWithLifecycle()
    val favoritesScope = rememberCoroutineScope()
    var showFavoritesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatalogMintBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (favoritoError.isNotBlank()) Text(favoritoError)
        // 1. Header Superior
        CatalogoHeaderBar(
            onProfile = onProfile,
            onOpenFavorites = { showFavoritesDialog = true; favoritesScope.launch { FavoritosManager.cargar() } },
            favoritesCount = favorites.size
        )

        if (preview) {
            PreviewNotice(preview = true)
        }

        // 2. Búsqueda
        SearchBarSection(
            searchQuery = state.filters.search,
            onSearchChange = {
                onEdit(state.filters.copy(search = it))
                onApply()
            }
        )

        // 3. Filtros
        FilterSection(
            options = state.options,
            filters = state.filters,
            onEditFilter = { newFilters ->
                onEdit(newFilters)
                onApply()
            }
        )

        // 4. Grid de Mascotas / Estados
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CatalogDarkGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cargando catálogo de mascotas...", fontSize = 14.sp, color = CatalogTextDark)
                    }
                }
            }
            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                }
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sin resultados. Prueba con otros filtros.", color = CatalogTextDark, fontSize = 15.sp)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.items, key = { it.id }) { mascota ->
                        PetCard(
                            mascota = mascota,
                            isFavorite = favorites.any { it.id == mascota.id },
                            onToggleFavorite = { FavoritosManager.toggleFavorite(mascota) },
                            onClick = { onDetail(mascota.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFavoritesDialog) {
        FavoritosDialog(
            favorites = favorites,
            onDismiss = { showFavoritesDialog = false },
            onRemoveFavorite = { FavoritosManager.removeFavorite(it) },
            onDetailPet = onDetail
        )
    }
}

@Composable
private fun CatalogoHeaderBar(
    onProfile: () -> Unit,
    onOpenFavorites: () -> Unit,
    favoritesCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo + Título
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = CatalogDarkGreen,
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
                    color = CatalogDarkGreen
                )
                Text(
                    text = "Catálogo",
                    fontSize = 12.sp,
                    color = Color(0xFF2E6C58)
                )
            }
        }

        // Acciones derecha
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
                        contentDescription = "Favoritos",
                        tint = if (favoritesCount > 0) Color(0xFFE53935) else CatalogDarkGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFFE4F5ED),
                modifier = Modifier
                    .height(34.dp)
                    .clickable(onClickLabel = "Mi cuestionario", onClick = onProfile)
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
                        color = CatalogDarkGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Buscar...", color = Color(0xFF81938A), fontSize = 15.sp) },
        leadingIcon = {
            Icon(
                imageVector = HomeIcons.Search,
                contentDescription = "Buscar",
                tint = CatalogDarkGreen,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = CatalogDarkGreen,
            unfocusedBorderColor = Color(0xFFD4E4DB)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FilterSection(
    options: CatalogoOptions?,
    filters: CatalogoFilters,
    onEditFilter: (CatalogoFilters) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Fila 1: Especie
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterPill(
                    label = "Todos",
                    isSelected = filters.especie == null,
                    onClick = { onEditFilter(filters.copy(especie = null)) }
                )
            }
            item {
                FilterPill(
                    label = "🐶 Perros",
                    isSelected = filters.especie == "Perro",
                    onClick = {
                        val next = if (filters.especie == "Perro") null else "Perro"
                        onEditFilter(filters.copy(especie = next))
                    }
                )
            }
            item {
                FilterPill(
                    label = "🐱 Gatos",
                    isSelected = filters.especie == "Gato",
                    onClick = {
                        val next = if (filters.especie == "Gato") null else "Gato"
                        onEditFilter(filters.copy(especie = next))
                    }
                )
            }
        }

        // Fila 2: Tamaño
        options?.tamanos?.let { tamanos ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Tamaño:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CatalogTextDark,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                item {
                    FilterPill(
                        label = "Todos",
                        isSelected = filters.tamano == null,
                        onClick = { onEditFilter(filters.copy(tamano = null)) }
                    )
                }
                items(tamanos, key = { it }) { tam ->
                    FilterPill(
                        label = tam,
                        isSelected = filters.tamano == tam,
                        onClick = {
                            val next = if (filters.tamano == tam) null else tam
                            onEditFilter(filters.copy(tamano = next))
                        }
                    )
                }
            }
        }

        // Fila 3: Temperamento
        options?.temperamentos?.let { temperamentos ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Temperamento:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CatalogTextDark,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                item {
                    FilterPill(
                        label = "Todos",
                        isSelected = filters.temperamento == null,
                        onClick = { onEditFilter(filters.copy(temperamento = null)) }
                    )
                }
                items(temperamentos, key = { it }) { temp ->
                    FilterPill(
                        label = temp,
                        isSelected = filters.temperamento == temp,
                        onClick = {
                            val next = if (filters.temperamento == temp) null else temp
                            onEditFilter(filters.copy(temperamento = next))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) CatalogDarkGreen else Color.White,
        shadowElevation = if (isSelected) 2.dp else 1.dp,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else CatalogDarkGreen
            )
        }
    }
}

@Composable
fun PetCard(
    mascota: Mascota,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                val imageUrl = mascota.fotografiaUrl?.takeIf { it.isNotBlank() }
                    ?: getFallbackImageUrl(mascota.nombre, mascota.especie)

                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Fotografía de ${mascota.nombre}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )

                // Icono Favorito superior izquierda (US-14)
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(32.dp)
                        .clickable { onToggleFavorite() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) HomeIcons.HeartFilled else HomeIcons.HeartOutlined,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                            tint = if (isFavorite) Color(0xFFE53935) else CatalogDarkGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Badge Flotante superior derecha
                val (badgeBg, badgeText) = when (mascota.estado.lowercase()) {
                    "adoptado" -> CatalogAdoptedOrange to Color(0xFF3B2404)
                    "en proceso" -> CatalogPillMint to CatalogTextDark
                    else -> Color(0xFF8DE2BA) to CatalogTextDark
                }

                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = mascota.estado,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = mascota.nombre,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CatalogTextDark
                )
                mascota.compatibilidad?.let { compatibilidad ->
                    Text(
                        text = compatibilidad.porcentaje?.let { "$it% compatible" }
                            ?: "Información insuficiente",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CatalogTextDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun getFallbackImageUrl(nombre: String, especie: String): String {
    return when (nombre.lowercase()) {
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
private fun CatalogoScreenPreview() {
    val sampleMascotas = listOf(
        Mascota(1, "Luna", "Perro", "Mediano", 24, "Tranquilo", "Media", "", "Refugio", "Lima", "Disponible"),
        Mascota(2, "Rocky", "Perro", "Grande", 36, "Activo", "Alta", "", "Refugio", "Guadalajara", "Disponible"),
        Mascota(3, "Milo", "Gato", "Pequeño", 8, "Tranquilo", "Baja", "", "Refugio", "Lima", "Adoptado"),
        Mascota(4, "Canela", "Perro", "Pequeño", 18, "Sociable", "Media", "", "Refugio", "Guadalajara", "Disponible"),
        Mascota(5, "Simba", "Gato", "Mediano", 18, "Sociable", "Media", "", "Refugio", "Lima", "Disponible"),
        Mascota(6, "Bella", "Perro", "Grande", 48, "Tranquilo", "Baja", "", "Refugio", "Guadalajara", "Adoptado"),
        Mascota(7, "Thor", "Perro", "Mediano", 30, "Activo", "Alta", "", "Refugio", "Lima", "Disponible"),
        Mascota(8, "Nala", "Gato", "Pequeño", 12, "Tranquilo", "Media", "", "Refugio", "Guadalajara", "Disponible")
    )
    val state = CatalogoUiState(
        items = sampleMascotas,
        options = CatalogoOptions(listOf("Perro", "Gato"), listOf("Pequeño", "Mediano", "Grande"), listOf("Tranquilo", "Activo"))
    )
    CatalogoScreen(
        state = state,
        preview = false,
        onEdit = {},
        onApply = {},
        onClear = {},
        onPage = {},
        onRetry = {},
        onDetail = {},
        onProfile = {},
        onExit = {}
    )
}
