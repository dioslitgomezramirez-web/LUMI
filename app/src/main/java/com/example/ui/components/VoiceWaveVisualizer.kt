package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun VoiceWaveVisualizer(
    amplitude: Float, // current amplitude level from 0f to 1f
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    
    // Constant crawling phase velocity
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_offset"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val baseMaxHeight = height * 0.4f // 40% height max limit

        // Colors representing the LYNOR pastels
        val waveColors = listOf(
            LumiSkyBlue to LumiViolet,
            LumiPink to LumiViolet,
            LumiGlowCyan to LumiSkyBlue
        )

        // Draw 3 layered waves of different speeds and heights
        for (i in 0 until 3) {
            val path = Path()
            val colorStart = waveColors[i].first
            val colorEnd = waveColors[i].second

            val waveAmplitude = baseMaxHeight * (0.15f + amplitude * 0.85f) * (1.0f - (i * 0.22f))
            val waveFrequency = 0.012f + (i * 0.004f)
            val wavePhase = phase * (1.2f - (i * 0.3f)) + (i * 1.51f)

            path.moveTo(0f, centerY)

            // Plot points across screen width
            var x = 0f
            val stepSize = 10f
            while (x <= width) {
                // Symmetrical windowing to make wave taper off nicely at left and right edges! (Super professional)
                val edgeFactor = sin((x / width) * Math.PI.toFloat())
                val y = centerY + waveAmplitude * edgeFactor * sin((x * waveFrequency) + wavePhase)
                
                path.lineTo(x, y)
                x += stepSize
            }

            // Brush for glowing glass gradient paths
            val brush = Brush.linearGradient(
                colors = listOf(
                    colorStart.copy(alpha = 0.5f - (i * 0.1f)),
                    colorEnd.copy(alpha = 0.3f - (i * 0.08f))
                )
            )

            drawPath(
                path = path,
                brush = brush,
                style = Stroke(
                    width = (3.dp - (i * 0.5f).dp).toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
