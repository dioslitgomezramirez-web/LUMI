package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    // Elegant translucent liquid-glass brush
    val glassBg = if (isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f), // white/10 top
                Color.White.copy(alpha = 0.02f)  // fading out towards bottom
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xD9FFFFFF), // 85% opacity on top
                Color(0xAAFFFFFF)  // 66% opacity on bottom
            )
        )
    }

    val glassBorder = if (isDarkMode) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.08f),
                LumiViolet.copy(alpha = 0.3f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(glassBg)
            .border(width = borderWidth, brush = glassBorder, shape = shape)
            .padding(1.dp)
    ) {
        // Subtle radial backdrop lighting for dark mode cards
        if (isDarkMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x0A26F1FF), Color.Transparent),
                            radius = 400f
                        )
                    )
            )
        }
        
        Box(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
