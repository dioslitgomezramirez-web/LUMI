package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun LumiAvatar(
    emotion: String, // "neutral", "happy", "thinking", "sad", "listening", "speaking"
    amplitude: Float, // Voice level from 0f to 1f
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lumi_infinite")

    // Gentle sinusoidal float offset
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    // Slow orbital rotation for particle rings
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    // Breathing pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "atmosphere_pulse"
    )

    // Blink timer (blinks once every 4 seconds)
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0
                1f at 3800
                0f at 3900 // closed eyes
                1f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eye_blink"
    )

    // Shifting aurora colors with time
    val auroraShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_shift"
    )

    // Emotional dynamic styling values
    val (primaryGlowColor, secondaryGlowColor) = when (emotion) {
        "happy" -> Pair(LumiPink, LumiViolet)
        "sad" -> Pair(Color(0xFF81D4FA), Color(0xFF90CAF9))
        "thinking" -> Pair(LumiViolet, LumiSkyBlue)
        "listening" -> Pair(LumiGlowCyan, LumiSkyBlue)
        "speaking" -> Pair(LumiPink, LumiGlowCyan)
        else -> Pair(LumiSkyBlue, LumiViolet) // neutral
    }

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatOffset.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val baseRadius = size.toPx() * 0.35f
            val maxRadius = baseRadius * pulseScale

            // 1. Draw Holographic Soft Glow / Aura Behind
            val dynamicRadius = maxRadius * (1f + (amplitude * 0.35f))
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to primaryGlowColor.copy(alpha = 0.35f),
                    0.5f to secondaryGlowColor.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = dynamicRadius * 1.6f
                ),
                radius = dynamicRadius * 1.6f,
                center = center
            )

            // 2. Liquid Glass outer boundary / Energy aura
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.4f),
                        secondaryGlowColor.copy(alpha = 0.1f),
                        LumiGlowCyan.copy(alpha = 0.3f),
                        primaryGlowColor.copy(alpha = 0.4f)
                    ),
                    center = center
                ),
                radius = dynamicRadius + 4f,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Draw Rotating orbital planetary dust/particles (Nothing OS / Futuristic flavor)
            rotate(rotationAngle, center) {
                // Orbit ring path
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = dynamicRadius * 1.35f,
                    center = center,
                    style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
                )

                // Particle dots
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = 4.dp.toPx(),
                    center = Offset(center.x + dynamicRadius * 1.35f, center.y)
                )
                drawCircle(
                    color = primaryGlowColor.copy(alpha = 0.7f),
                    radius = 3.dp.toPx(),
                    center = Offset(center.x - dynamicRadius * 1.35f, center.y)
                )

                // Minor oblique star
                drawCircle(
                    color = LumiGlowCyan.copy(alpha = 0.5f),
                    radius = 2.dp.toPx(),
                    center = Offset(center.x, center.y + dynamicRadius * 1.35f)
                )
            }

            // 4. Draw Core Liquid Glass body (Blur depth)
            val bodyBrush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = 0.9f),
                0.4f to Color.White.copy(alpha = 0.7f),
                0.8f to primaryGlowColor.copy(alpha = 0.15f),
                1.0f to Color.White.copy(alpha = 0.05f),
                center = center.copy(y = center.y - (baseRadius * 0.2f)), // light source from top-ish
                radius = baseRadius
            )
            drawCircle(
                brush = bodyBrush,
                radius = baseRadius,
                center = center
            )

            // Soft white glass specular highlight on top
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    startY = center.y - baseRadius,
                    endY = center.y
                ),
                radius = baseRadius * 0.9f,
                center = center
            )

            // 5. Draw Animated eyes for expressions
            val eyeWidth = 10.dp.toPx()
            val eyeSpacing = 24.dp.toPx()
            val eyeYOffset = 5.dp.toPx()

            val leftEyeCenter = Offset(center.x - eyeSpacing, center.y - eyeYOffset)
            val rightEyeCenter = Offset(center.x + eyeSpacing, center.y - eyeYOffset)

            // Dynamic soft white eye glow shadows matching spec: shadow-[0_0_10px_white]
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.45f),
                    1.0f to Color.Transparent,
                    center = leftEyeCenter,
                    radius = 14.dp.toPx()
                ),
                radius = 14.dp.toPx(),
                center = leftEyeCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.45f),
                    1.0f to Color.Transparent,
                    center = rightEyeCenter,
                    radius = 14.dp.toPx()
                ),
                radius = 14.dp.toPx(),
                center = rightEyeCenter
            )

            when (emotion) {
                "happy" -> {
                    // Cute smiling sparkling arches: ^ ^
                    val eyeSize = Size(eyeWidth * 1.5f, eyeWidth * 1.5f)
                    
                    // Left Arc
                    drawArc(
                        color = Color.White,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(leftEyeCenter.x - eyeSize.width/2, leftEyeCenter.y - eyeSize.height/2),
                        size = eyeSize,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Right Arc
                    drawArc(
                        color = Color.White,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(rightEyeCenter.x - eyeSize.width/2, rightEyeCenter.y - eyeSize.height/2),
                        size = eyeSize,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Sparkles inside the aura
                    drawCircle(
                        color = LumiPink,
                        radius = 2.dp.toPx(),
                        center = Offset(center.x, center.y + 12.dp.toPx())
                    )
                }
                "sad" -> {
                    // Slanted drooping lines / curve arches: \ / or sensitive circles
                    val eyeSize = Size(eyeWidth * 1.4f, eyeWidth * 0.8f)

                    // Left sad drooping arc
                    drawArc(
                        color = Color.White.copy(alpha = 0.9f),
                        startAngle = 0f,
                        sweepAngle = -180f,
                        useCenter = false,
                        topLeft = Offset(leftEyeCenter.x - eyeSize.width/2, leftEyeCenter.y - eyeSize.height/4),
                        size = eyeSize,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Right sad drooping arc
                    drawArc(
                        color = Color.White.copy(alpha = 0.9f),
                        startAngle = 0f,
                        sweepAngle = -180f,
                        useCenter = false,
                        topLeft = Offset(rightEyeCenter.x - eyeSize.width/2, rightEyeCenter.y - eyeSize.height/4),
                        size = eyeSize,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                "thinking" -> {
                    // Moving mechanical circles centered
                    val radialSpeed = rotationAngle * 2.5f

                    // Rotate dash loop around eyes
                    drawCircle(
                        color = LumiViolet.copy(alpha = 0.8f),
                        radius = baseRadius * 0.5f,
                        center = center,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), radialSpeed)
                        )
                    )

                    // Small subtle curious dots for eyes
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = leftEyeCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = rightEyeCenter
                    )
                }
                "listening" -> {
                    // Glow rings around eyes, pulsing eye height
                    val hearingPulse = 1f + (amplitude * 0.5f)
                    
                    // Glowing background ring centered around the entire face
                    drawCircle(
                        color = LumiGlowCyan.copy(alpha = 0.5f),
                        radius = baseRadius * 0.65f * hearingPulse,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx() * hearingPulse,
                        center = leftEyeCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx() * hearingPulse,
                        center = rightEyeCenter
                    )
                }
                "speaking" -> {
                    // Wave vibrations under the eyes, eyes widening slightly
                    val speakingIntensity = 1f + (amplitude * 0.4f)
                    
                    // Draw lips/vibration line
                    drawLine(
                        color = LumiGlowCyan,
                        start = Offset(center.x - 12.dp.toPx(), center.y + 10.dp.toPx()),
                        end = Offset(center.x + 12.dp.toPx(), center.y + 10.dp.toPx()),
                        strokeWidth = (2.dp.toPx() + (amplitude * 4.dp.toPx())),
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 5.5.dp.toPx() * speakingIntensity,
                        center = leftEyeCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.5.dp.toPx() * speakingIntensity,
                        center = rightEyeCenter
                    )
                }
                else -> {
                    // "neutral" - standard round vertical capsule blinking eyes
                    val currentEyeHeight = eyeWidth * blinkProgress
                    
                    if (currentEyeHeight > 1.5f) {
                        // Vertical capsule shape
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = leftEyeCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = rightEyeCenter
                        )
                    } else {
                        // Sleeping/blink horizontal bar
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f),
                            start = Offset(leftEyeCenter.x - 5.dp.toPx(), leftEyeCenter.y),
                            end = Offset(leftEyeCenter.x + 5.dp.toPx(), leftEyeCenter.y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f),
                            start = Offset(rightEyeCenter.x - 5.dp.toPx(), rightEyeCenter.y),
                            end = Offset(rightEyeCenter.x + 5.dp.toPx(), rightEyeCenter.y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
