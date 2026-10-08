package com.petly_app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreviewNotice(preview: Boolean) {
    if (preview) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Text("Vista de prueba · datos ficticios · guardado temporal", modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}
