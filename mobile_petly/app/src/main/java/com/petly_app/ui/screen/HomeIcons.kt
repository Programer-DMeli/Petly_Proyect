package com.petly_app.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object HomeIcons {
    private val line = SolidColor(Color.Black)

    val Search = ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(11f, 19f)
            curveTo(15.4183f, 19f, 19f, 15.4183f, 19f, 11f)
            curveTo(19f, 6.58172f, 15.4183f, 3f, 11f, 3f)
            curveTo(6.58172f, 3f, 3f, 6.58172f, 3f, 11f)
            curveTo(3f, 15.4183f, 6.58172f, 19f, 11f, 19f)
            close()
            moveTo(21f, 21f)
            lineTo(16.65f, 16.65f)
        }
    }.build()

    val Dog = ImageVector.Builder("DogIcon", 28.dp, 28.dp, 28f, 28f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 7f)
            curveTo(4f, 7f, 3f, 10f, 3f, 13f)
            curveTo(3f, 16f, 5f, 17f, 7f, 16f)
            moveTo(7f, 16f)
            curveTo(7f, 21f, 10f, 24f, 14f, 24f)
            curveTo(18f, 24f, 21f, 21f, 21f, 16f)
            moveTo(21f, 16f)
            curveTo(23f, 17f, 25f, 16f, 25f, 13f)
            curveTo(25f, 10f, 24f, 7f, 22f, 7f)
            moveTo(6f, 7f)
            curveTo(10f, 4f, 18f, 4f, 22f, 7f)
            moveTo(11f, 16f)
            curveTo(12.5f, 14.5f, 15.5f, 14.5f, 17f, 16f)
            curveTo(15.5f, 18.5f, 12.5f, 18.5f, 11f, 16f)
        }
    }.build()

    val Cat = ImageVector.Builder("CatIcon", 28.dp, 28.dp, 28f, 28f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 12f)
            curveTo(4f, 12f, 3f, 16f, 3f, 20f)
            curveTo(3f, 24f, 7f, 26f, 14f, 26f)
            curveTo(21f, 26f, 25f, 24f, 25f, 20f)
            curveTo(25f, 16f, 24f, 12f, 21f, 12f)
            moveTo(7f, 12f)
            lineTo(8f, 3f)
            lineTo(13f, 10f)
            moveTo(21f, 12f)
            lineTo(20f, 3f)
            lineTo(15f, 10f)
            moveTo(12f, 18f)
            lineTo(14f, 20f)
            lineTo(16f, 18f)
        }
    }.build()

    val CompanionHeart = ImageVector.Builder("CompanionHeart", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 20f)
            curveTo(7f, 16f, 4f, 12f, 4f, 8.5f)
            curveTo(4f, 5.5f, 6.5f, 3.5f, 9.5f, 3.5f)
            curveTo(11.2f, 3.5f, 12.8f, 4.3f, 13.8f, 5.5f)
            curveTo(14.8f, 4.3f, 16.4f, 3.5f, 18.1f, 3.5f)
            curveTo(21.1f, 3.5f, 23.5f, 5.5f, 23.5f, 8.5f)
            curveTo(23.5f, 12f, 20.5f, 16f, 15.5f, 20f)
            close()
            moveTo(3f, 18f)
            curveTo(6f, 16f, 10f, 18f, 12f, 21f)
        }
    }.build()

    val Check = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    }.build()

    val LocationPin = ImageVector.Builder("LocationPin", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
            curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()
            moveTo(12f, 11.5f)
            curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
            curveTo(9.5f, 7.62f, 10.62f, 6.5f, 12f, 6.5f)
            curveTo(13.38f, 6.5f, 14.5f, 7.62f, 14.5f, 9f)
            curveTo(14.5f, 10.38f, 13.38f, 11.5f, 12f, 11.5f)
            close()
        }
    }.build()

    val HomeFilled = ImageVector.Builder("HomeFilled", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = line) {
            moveTo(10f, 20f)
            lineTo(10f, 14f)
            lineTo(14f, 14f)
            lineTo(14f, 20f)
            lineTo(19f, 20f)
            lineTo(19f, 12f)
            lineTo(22f, 12f)
            lineTo(12f, 3f)
            lineTo(2f, 12f)
            lineTo(5f, 12f)
            lineTo(5f, 20f)
            close()
        }
    }.build()

    val HomeOutlined = ImageVector.Builder("HomeOutlined", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 10.5f)
            lineTo(12f, 3f)
            lineTo(21f, 10.5f)
            lineTo(21f, 20f)
            curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
            lineTo(4f, 21f)
            curveTo(3.45f, 21f, 3f, 20.55f, 3f, 20f)
            close()
            moveTo(9f, 21f)
            lineTo(9f, 14f)
            lineTo(15f, 14f)
            lineTo(15f, 21f)
        }
    }.build()

    val Bell = ImageVector.Builder("Bell", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(18f, 8f)
            curveTo(18f, 4.69f, 15.31f, 2f, 12f, 2f)
            curveTo(8.69f, 2f, 6f, 4.69f, 6f, 8f)
            curveTo(6f, 15f, 3f, 17f, 3f, 17f)
            lineTo(21f, 17f)
            curveTo(21f, 17f, 18f, 15f, 18f, 8f)
            close()
            moveTo(13.73f, 21f)
            curveTo(13.55f, 21.3f, 13.3f, 21.55f, 13f, 21.71f)
            curveTo(12.7f, 21.87f, 12.35f, 21.96f, 12f, 21.96f)
            curveTo(11.65f, 21.96f, 11.3f, 21.87f, 11f, 21.71f)
            curveTo(10.7f, 21.55f, 10.45f, 21.3f, 10.27f, 21f)
        }
    }.build()

    val HeartOutlined = ImageVector.Builder("HeartOutlined", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = line, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 21.35f)
            lineTo(10.55f, 20.03f)
            curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
            curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
            curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
            curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
            curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
            curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
            close()
        }
    }.build()
}
