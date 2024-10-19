package com.petly_app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

data class NovedadItem(
    val id: Long,
    val titulo: String,
    val fecha: String,
    val descripcion: String,
    val imagenUrl: String
)

private val novedadesDemo = listOf(
    NovedadItem(
        id = 1,
        titulo = "Jornada de adopción en Guadalajara",
        fecha = "15 de Octubre, 2024",
        descripcion = "Acompáñanos este fin de semana en el Parque Metropolitano para conocer a más de 30 mascotas que buscan un hogar amoroso.",
        imagenUrl = "https://images.unsplash.com/photo-1548767797-d8c844163c4c?q=80&w=1000"
    ),
    NovedadItem(
        id = 2,
        titulo = "Consejos para la llegada de tu nueva mascota",
        fecha = "10 de Octubre, 2024",
        descripcion = "Descubre los pasos fundamentales para que la transición a su nuevo hogar sea una experiencia positiva y segura.",
        imagenUrl = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?q=80&w=1000"
    ),
    NovedadItem(
        id = 3,
        titulo = "Campaña de vacunación y esterilización gratuita",
        fecha = "02 de Octubre, 2024",
        descripcion = "Junto al Refugio Patitas Felices realizaremos atención veterinaria preventiva para la comunidad.",
        imagenUrl = "https://images.unsplash.com/photo-1601758228041-f3b2795255f1?q=80&w=1000"
    )
)

@Composable
fun NovedadesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenMintBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Novedades Petly",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PetlyTextDark,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(novedadesDemo, key = { it.id }) { novedad ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            AsyncImage(
                                model = novedad.imagenUrl,
                                contentDescription = novedad.titulo,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = novedad.fecha,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PetlyDarkGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = novedad.titulo,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PetlyTextDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = novedad.descripcion,
                            fontSize = 13.sp,
                            color = Color(0xFF4A6258)
                        )
                    }
                }
            }
        }
    }
}
