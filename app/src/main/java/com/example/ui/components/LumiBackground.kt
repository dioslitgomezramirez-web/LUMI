package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LumiBackground(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "background_waves")

    // Slow orbital offsets for gradient centers
    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_1"
    )

    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(35000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_2"
    )

    val baseGradient = if (isDarkMode) {
        // Deep cosmic AMOLED gradient
        Brush.verticalGradient(
            colors = listOf(LumiAMOLEDBlack, LumiDeepSlate, LumiAMOLEDBlack)
        )
    } else {
        // Light, clean pearl white gradient
        Brush.verticalGradient(
            colors = listOf(LumiPearlWhite, Color(0xFFF0F2F5))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseGradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Calculate moving center 1 orbiting in upper region
            val cx1 = width * 0.3f + (width * 0.2f * cos(t1))
            val cy1 = height * 0.25f + (height * 0.15f * sin(t1))

            // Calculate moving center 2 orbiting in lower/center region
            val cx2 = width * 0.7f + (width * 0.25f * sin(t2))
            val cy2 = height * 0.7f + (height * 0.12f * cos(t2))

            if (isDarkMode) {
                // Drawing Deep Space Sophisticated Atmospheric Neon Auroras
                // Aurora 1 (Atmospheric elegant blue glow: #3440ff)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFF3440FF).copy(alpha = 0.12f),
                        0.6f to Color(0xFF3440FF).copy(alpha = 0.04f),
                        1.0f to Color.Transparent,
                        center = Offset(cx1, cy1),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(cx1, cy1)
                )

                // Aurora 2 (Atmospheric elegant deep magenta/pink glow: #ff4ef2)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFFFF4EF2).copy(alpha = 0.10f),
                        0.6f to Color(0xFFFF4EF2).copy(alpha = 0.03f),
                        1.0f to Color.Transparent,
                        center = Offset(cx2, cy2),
                        radius = width * 0.95f
                    ),
                    radius = width * 0.95f,
                    center = Offset(cx2, cy2)
                )
            } else {
                // Drawing Soft Pastel Pearl Clouds
                // Cloud 1 (Violet pastel)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to LumiViolet.copy(alpha = 0.22f),
                        0.6f to LumiSkyBlue.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                        center = Offset(cx1, cy1),
                        radius = width * 0.65f
                    ),
                    radius = width * 0.65f,
                    center = Offset(cx1, cy1)
                )

                // Cloud 2 (Pink pastel)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to LumiPink.copy(alpha = 0.18f),
                        0.5f to LumiViolet.copy(alpha = 0.06f),
                        1.0f to Color.Transparent,
                        center = Offset(cx2, cy2),
                        radius = width * 0.75f
                    ),
                    radius = width * 0.75f,
                    center = Offset(cx2, cy2)
                )
            }
        }
        
        content()
    }
}
