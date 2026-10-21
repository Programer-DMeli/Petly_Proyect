package com.petly_app.ui.catalogo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petly_app.data.model.Mascota
import com.petly_app.ui.screen.HomeIcons

@Composable
fun FavoritosDialog(
    favorites: List<Mascota>,
    onDismiss: () -> Unit,
    onRemoveFavorite: (Long) -> Unit,
    onDetailPet: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = HomeIcons.HeartFilled,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Mis Mascotas Favoritas",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3A2E)
                )
            }
        },
        text = {
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Aún no has guardado mascotas favoritas.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F3A2E)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Explora el catálogo y toca el icono ❤️ en las mascotas que más te gusten para guardarlas aquí.",
                        fontSize = 13.sp,
                        color = Color(0xFF4A6258)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favorites, key = { it.id }) { pet ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF4F9F6),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onDetailPet(pet.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val imageUrl = pet.fotografiaUrl?.takeIf { it.isNotBlank() }
                                    ?: "https://images.unsplash.com/photo-1543466835-00a7907e9de1?q=80&w=1000"

                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = pet.nombre,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pet.nombre,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F3A2E)
                                    )
                                    Text(
                                        text = "${pet.especie} · ${pet.tamano}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4A6258)
                                    )
                                }

                                IconButton(onClick = { onRemoveFavorite(pet.id) }) {
                                    Icon(
                                        imageVector = HomeIcons.HeartFilled,
                                        contentDescription = "Quitar de favoritos",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", fontWeight = FontWeight.Bold, color = Color(0xFF13614B))
            }
        }
    )
}
