package dev.andikune.robotasisten.io.ui.chat.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient animated Aurora Mesh Gradient background reminiscent of Gemini's voice interface.
 * Runs smoothly on the lowest z-index layer without compromising text readability.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    // Slow organic motion transitions
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712)) // Deep dark space background
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Center 1: Cyber Teal / Deep Cyan
            val cx1 = width * 0.35f + cos(time) * (width * 0.25f)
            val cy1 = height * 0.25f + sin(time) * (height * 0.15f)
            val r1 = (width.coerceAtLeast(height) * 0.85f) * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D9488).copy(alpha = 0.22f), // Cyan Teal
                        Color(0xFF0F766E).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(cx1, cy1),
                    radius = r1
                ),
                radius = r1,
                center = Offset(cx1, cy1)
            )

            // Center 2: Deep Indigo / Electric Purple
            val cx2 = width * 0.65f - sin(time * 0.8f) * (width * 0.28f)
            val cy2 = height * 0.68f + cos(time * 0.8f) * (height * 0.18f)
            val r2 = (width.coerceAtLeast(height) * 0.95f) * (2f - pulse)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.20f), // Indigo
                        Color(0xFF4338CA).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx2, cy2),
                    radius = r2
                ),
                radius = r2,
                center = Offset(cx2, cy2)
            )

            // Center 3: Deep Slate / Navy anchor at bottom
            val cx3 = width * 0.5f + sin(time * 1.2f) * (width * 0.15f)
            val cy3 = height * 0.95f
            val r3 = width * 1.1f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E293B).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(cx3, cy3),
                    radius = r3
                ),
                radius = r3,
                center = Offset(cx3, cy3)
            )
        }

        content()
    }
}
