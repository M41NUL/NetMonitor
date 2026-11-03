package com.netmonitor.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
        else listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.50f))
    )
    val edge = Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))
        else listOf(Color.White, Color.Black.copy(alpha = 0.06f))
    )
    return this.clip(shape).background(fill, shape).border(1.dp, edge, shape)
}

@Composable
fun Modifier.plainCard(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val cs = MaterialTheme.colorScheme
    return this.clip(shape).background(cs.surfaceVariant.copy(alpha = 0.5f), shape)
        .border(1.dp, cs.outlineVariant, shape)
}

@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val a = if (dark) 0.30f else 0.20f
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .drawBehind {
                fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                    val center = Offset(cx, cy)
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = center, radius = r),
                        radius = r,
                        center = center,
                    )
                }
                blob(DownBlue, size.width * 0.10f, size.height * 0.12f, size.width * 0.90f)
                blob(Color(0xFF1D9E75), size.width * 0.95f, size.height * 0.45f, size.width * 0.80f)
                blob(Color(0xFF7F77DD), size.width * 0.15f, size.height * 0.85f, size.width * 0.90f)
            }
    ) { content() }
}
