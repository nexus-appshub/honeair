package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Official 4-Color Google "G" Brand Logo Composable rendered with exact official SVG Vector Paths.
 */
@Composable
fun OfficialGoogleLogo(
    modifier: Modifier = Modifier.size(24.dp)
) {
    val redPath = remember {
        PathParser().parsePathString(
            "M 12 4.75 C 13.77 4.75 15.35 5.36 16.6 6.55 L 20.03 3.12 C 17.95 1.19 15.24 0 12 0 C 7.33 0 3.29 2.69 1.28 6.61 L 5.27 9.71 C 6.21 6.86 8.87 4.75 12 4.75 Z"
        ).toPath()
    }
    val yellowPath = remember {
        PathParser().parsePathString(
            "M 5.27 14.29 C 5.03 13.57 4.9 12.8 4.9 12 C 4.9 11.2 5.03 10.43 5.27 9.71 L 1.28 6.61 C 0.46 8.24 0 10.06 0 12 C 0 13.94 0.46 15.76 1.28 17.39 L 5.27 14.29 Z"
        ).toPath()
    }
    val greenPath = remember {
        PathParser().parsePathString(
            "M 12 24 C 15.24 24 17.96 22.93 19.94 21.09 L 16.27 18.24 C 15.25 18.93 13.77 19.35 12 19.35 C 8.87 19.35 6.21 17.24 5.27 14.29 L 1.28 17.39 C 3.29 21.31 7.33 24 12 24 Z"
        ).toPath()
    }
    val bluePath = remember {
        PathParser().parsePathString(
            "M 23.49 12.27 C 23.49 11.48 23.42 10.73 23.3 10 C 21.05 10 12 10 12 10 V 14.51 H 18.47 C 18.18 15.99 17.34 17.25 16.27 18.24 L 19.94 21.09 C 22.09 19.11 23.49 16.14 23.49 12.27 Z"
        ).toPath()
    }

    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f

        withTransform({
            scale(scaleX, scaleY, pivot = Offset.Zero)
        }) {
            drawPath(path = redPath, color = Color(0xFFEA4335))
            drawPath(path = yellowPath, color = Color(0xFFFBBC05))
            drawPath(path = greenPath, color = Color(0xFF34A853))
            drawPath(path = bluePath, color = Color(0xFF4285F4))
        }
    }
}

