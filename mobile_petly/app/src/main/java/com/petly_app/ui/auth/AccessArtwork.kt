package com.petly_app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small vector assets stay sharp at every Android density without another icon dependency. */
internal object AccessIcons {
    private val line = SolidColor(Color.Black)
    val Person = ImageVector.Builder("Person", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(16f, 7f); curveTo(16f, 12f, 8f, 12f, 8f, 7f); curveTo(8f, 2f, 16f, 2f, 16f, 7f); close()
            moveTo(4f, 21f); lineTo(4f, 18f); curveTo(4f, 12f, 20f, 12f, 20f, 18f); lineTo(20f, 21f); close()
        }
    }.build()
    val Mail = ImageVector.Builder("Mail", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(2f, 5f); lineTo(22f, 5f); lineTo(22f, 19f); lineTo(2f, 19f); close()
            moveTo(2f, 5f); lineTo(12f, 13f); lineTo(22f, 5f)
        }
    }.build()
    val Lock = ImageVector.Builder("Lock", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 10f); lineTo(19f, 10f); lineTo(19f, 22f); lineTo(5f, 22f); close()
            moveTo(8f, 10f); lineTo(8f, 6f); curveTo(8f, 0f, 16f, 0f, 16f, 6f); lineTo(16f, 10f)
            moveTo(12f, 14f); lineTo(12f, 18f)
        }
    }.build()
    val Check = ImageVector.Builder("CheckCircle", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(21f, 12f); curveTo(21f, 25f, 3f, 25f, 3f, 12f); curveTo(3f, 3f, 11f, 1f, 17f, 4f)
            moveTo(7f, 11f); lineTo(11f, 15f); lineTo(21f, 4f)
        }
    }.build()
    val Paw = ImageVector.Builder("Paw", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = line) {
            moveTo(12f, 10f); curveTo(8f, 10f, 7f, 14f, 5f, 16f)
            curveTo(1f, 22f, 9f, 23f, 12f, 20f); curveTo(15f, 23f, 23f, 22f, 19f, 16f)
            curveTo(17f, 14f, 16f, 10f, 12f, 10f); close()
            moveTo(10f, 5f); curveTo(10f, 10f, 4f, 9f, 5f, 4f); curveTo(6f, 0f, 10f, 1f, 10f, 5f); close()
            moveTo(19f, 4f); curveTo(20f, 9f, 14f, 10f, 14f, 5f); curveTo(14f, 1f, 18f, 0f, 19f, 4f); close()
            moveTo(5f, 10f); curveTo(7f, 15f, 1f, 16f, 0f, 11f); curveTo(-1f, 7f, 3f, 6f, 5f, 10f); close()
            moveTo(24f, 11f); curveTo(23f, 16f, 17f, 15f, 19f, 10f); curveTo(21f, 6f, 25f, 7f, 24f, 11f); close()
        }
    }.build()
    val Eye = ImageVector.Builder("Visibility", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(2f, 12f); curveTo(6f, 5f, 18f, 5f, 22f, 12f)
            curveTo(18f, 19f, 6f, 19f, 2f, 12f); close()
            moveTo(15f, 12f); curveTo(15f, 16f, 9f, 16f, 9f, 12f)
            curveTo(9f, 8f, 15f, 8f, 15f, 12f); close()
        }
    }.build()
    val Arrow = ImageVector.Builder("ArrowForward", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(13f, 5f); lineTo(20f, 12f); lineTo(13f, 19f)
        }
    }.build()
    val Heart = ImageVector.Builder("Heart", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = line) {
            moveTo(12f, 21f); curveTo(9f, 18f, 2f, 14f, 2f, 8f)
            curveTo(2f, 2f, 9f, 1f, 12f, 6f)
            curveTo(15f, 1f, 22f, 2f, 22f, 8f)
            curveTo(22f, 14f, 15f, 18f, 12f, 21f); close()
        }
    }.build()
    val Fingerprint = ImageVector.Builder("Fingerprint", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 6f); curveTo(8f, 1f, 16f, 1f, 20f, 6f)
            moveTo(2f, 12f); curveTo(2f, 3f, 22f, 3f, 22f, 12f)
            moveTo(3f, 17f); curveTo(0f, 3f, 21f, 3f, 20f, 14f)
            moveTo(7f, 21f); curveTo(0f, 6f, 18f, 4f, 18f, 13f)
            curveTo(18f, 17f, 19f, 19f, 21f, 20f)
            moveTo(10f, 22f); curveTo(6f, 17f, 5f, 10f, 11f, 10f)
            curveTo(18f, 9f, 12f, 17f, 18f, 22f)
            moveTo(14f, 22f); curveTo(11f, 18f, 10f, 15f, 11f, 13f)
        }
    }.build()
    // Original vector interpretation of the people, companion and heart in the supplied logo.
    val Companions = ImageVector.Builder("PetlyCompanions", 44.dp, 40.dp, 44f, 40f).apply {
        path(fill = null, stroke = SolidColor(Color(0xFF126858)), strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(20f, 10f); curveTo(23f, 2f, 12f, 0f, 10f, 7f)
            curveTo(7f, 14f, 17f, 15f, 20f, 10f); close()
            moveTo(14f, 17f); curveTo(0f, 17f, 2f, 35f, 14f, 35f)
            moveTo(28f, 15f); curveTo(28f, 8f, 34f, 7f, 37f, 12f)
            lineTo(37f, 19f); curveTo(47f, 27f, 39f, 37f, 30f, 35f)
            moveTo(29f, 13f); lineTo(25f, 17f); lineTo(29f, 19f)
        }
        path(fill = SolidColor(Color(0xFFDF7C79)), pathFillType = PathFillType.NonZero) {
            moveTo(22f, 36f); curveTo(18f, 33f, 12f, 29f, 13f, 24f)
            curveTo(14f, 18f, 20f, 19f, 22f, 23f)
            curveTo(26f, 17f, 33f, 20f, 32f, 25f)
            curveTo(32f, 29f, 25f, 34f, 22f, 36f); close()
        }
    }.build()
}

@Composable
internal fun PetlyAccessLogo() {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.size(104.dp).semantics(mergeDescendants = true) { contentDescription = "Petly, animales y adopciones" },
            shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) {
                Column(Modifier.size(74.dp).background(Color(0xFFF4F5F3)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(AccessIcons.Companions, null, tint = Color.Unspecified, modifier = Modifier.size(38.dp))
                    Text("Petly", color = Color(0xFF126858), fontFamily = FontFamily.SansSerif,
                        fontSize = 21.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Animales y Adopciones", color = Color(0xFF52635C), fontSize = 3.sp, lineHeight = 5.sp)
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).size(30.dp), shape = CircleShape, color = Color(0xFFFFBC5E)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AccessIcons.Heart, null, tint = Color(0xFF7A510A), modifier = Modifier.size(16.dp))
            }
        }
    }
}
