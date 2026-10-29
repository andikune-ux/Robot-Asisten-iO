package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.ui.theme.CyanAccent
import dev.andikune.robotasisten.io.ui.theme.SurfaceDark
import dev.andikune.robotasisten.io.ui.theme.TechBluePrimary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun OverlayBubble(
    status: BubbleStatus,
    sizeDp: Int = 58,
    opacity: Float = 0.92f,
    statusText: String? = null,
    isMinimized: Boolean = false,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robot_anim")

    // Active floating breathing movement
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    // Pulse wave for active operations
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (status != BubbleStatus.IDLE) 1.28f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (status == BubbleStatus.SCANNING || status == BubbleStatus.ANSWERING) 900 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (status != BubbleStatus.IDLE) 900 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Scanning radar line sweep
    val scanSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_sweep"
    )

    // Thinking rotation aura
    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_rotation"
    )

    // Eye blink state
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2800..4500).random().toLong())
            isBlinking = true
            delay(140)
            isBlinking = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.padding(4.dp)
    ) {
        // Main Robot Circle Container
        Box(
            modifier = Modifier
                .offset { IntOffset(0, floatAnim.roundToInt()) }
                .size(sizeDp.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { onLongPress() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Outer dynamic glowing pulse ring
            Box(
                modifier = Modifier
                    .size((sizeDp * 1.15).dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(status.color.copy(alpha = pulseAlpha))
            )

            // Thinking rotating aura border
            if (status == BubbleStatus.ANALYZING) {
                Box(
                    modifier = Modifier
                        .size((sizeDp + 4).dp)
                        .rotate(auraRotation)
                        .clip(CircleShape)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    status.color.copy(alpha = 0.1f),
                                    status.color,
                                    CyanAccent,
                                    status.color.copy(alpha = 0.1f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Core robot body
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .shadow(8.dp, CircleShape, spotColor = status.color)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1B2B3A),
                                SurfaceDark,
                                Color(0xFF0B1219)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                status.color.copy(alpha = opacity),
                                CyanAccent.copy(alpha = opacity * 0.7f),
                                TechBluePrimary.copy(alpha = opacity * 0.5f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Robot Vector Base
                Icon(
                    painter = painterResource(id = R.drawable.ic_robot),
                    contentDescription = "RAILX Robot",
                    tint = Color.Unspecified,
                    modifier = Modifier.size((sizeDp * 0.65).dp)
                )

                // Dynamic Eye & Scanner Expressions overlay
                Canvas(
                    modifier = Modifier
                        .size((sizeDp * 0.45).dp)
                        .align(Alignment.Center)
                ) {
                    val w = size.width
                    val h = size.height

                    when (status) {
                        BubbleStatus.SCANNING -> {
                            // Scanner beam sweep across eye visor
                            val sweepY = h * 0.35f + (h * 0.3f * scanSweep)
                            drawLine(
                                color = CyanAccent,
                                start = Offset(w * 0.15f, sweepY),
                                end = Offset(w * 0.85f, sweepY),
                                strokeWidth = 3.5f
                            )
                        }
                        BubbleStatus.ANALYZING -> {
                            // Dual thinking pupil circles
                            val eyeR = w * 0.09f
                            drawCircle(
                                color = status.color,
                                radius = eyeR,
                                center = Offset(w * 0.32f, h * 0.48f)
                            )
                            drawCircle(
                                color = status.color,
                                radius = eyeR,
                                center = Offset(w * 0.68f, h * 0.48f)
                            )
                        }
                        BubbleStatus.READY -> {
                            // Cheerful arc eyes
                            val stroke = Stroke(width = 3.5f)
                            drawArc(
                                color = status.color,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(w * 0.22f, h * 0.40f),
                                size = Size(w * 0.22f, h * 0.16f),
                                style = stroke
                            )
                            drawArc(
                                color = status.color,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(w * 0.56f, h * 0.40f),
                                size = Size(w * 0.22f, h * 0.16f),
                                style = stroke
                            )
                        }
                        BubbleStatus.ERROR -> {
                            // Alert eyes (red warning bars)
                            drawRoundRect(
                                color = status.color,
                                topLeft = Offset(w * 0.26f, h * 0.46f),
                                size = Size(w * 0.16f, h * 0.08f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                            drawRoundRect(
                                color = status.color,
                                topLeft = Offset(w * 0.58f, h * 0.46f),
                                size = Size(w * 0.16f, h * 0.08f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                        else -> {
                            // IDLE / ANSWERING: Expressive glowing eyes with natural blinking
                            val eyeH = if (isBlinking) h * 0.02f else h * 0.13f
                            val eyeW = w * 0.14f
                            drawRoundRect(
                                color = if (status == BubbleStatus.ANSWERING) CyanAccent else Color(0xFF67E8F9),
                                topLeft = Offset(w * 0.26f, h * 0.48f - (eyeH / 2f)),
                                size = Size(eyeW, eyeH),
                                cornerRadius = CornerRadius(eyeW / 2, eyeH / 2)
                            )
                            drawRoundRect(
                                color = if (status == BubbleStatus.ANSWERING) CyanAccent else Color(0xFF67E8F9),
                                topLeft = Offset(w * 0.60f, h * 0.48f - (eyeH / 2f)),
                                size = Size(eyeW, eyeH),
                                cornerRadius = CornerRadius(eyeW / 2, eyeH / 2)
                            )
                        }
                    }
                }
            }
        }

        // Minimized real-time dynamic text speech bubble beside robot
        if (isMinimized && !statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            StatusSpeechBubble(
                text = statusText,
                status = status,
                onClick = onTap
            )
        }
    }
}
