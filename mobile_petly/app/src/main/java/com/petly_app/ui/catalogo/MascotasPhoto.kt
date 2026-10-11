package com.petly_app.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MascotaPhoto(url: String?, nombre: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(model = url, contentDescription = "Fotografía de $nombre",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true }, onError = { failed = true })
            if (!loaded) Text("Cargando fotografía…", modifier = Modifier.padding(12.dp))
        } else Text("Fotografía no disponible", modifier = Modifier.padding(12.dp))
    }
}
