package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.SapphireBlue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrb(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    isThinking: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isListening -> 2400
                    isThinking -> 1800
                    isSpeaking -> 3000
                    else -> 8000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening || isSpeaking) 750 else 1800
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val primaryGlow = when {
        isListening -> EmeraldPulse
        isSpeaking -> ElectricCyan
        isThinking -> NeuralViolet
        else -> SapphireBlue
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f

        // Outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.32f * pulse),
                    primaryGlow.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Outer segmented arc
        val outerRadius = radius * 0.84f
        drawArc(
            color = primaryGlow.copy(alpha = 0.85f),
            startAngle = rotation,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = ElectricCyan.copy(alpha = 0.65f),
            startAngle = rotation + 180f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Inner counter-rotating ring
        val midRadius = radius * 0.64f
        drawArc(
            color = NeuralViolet.copy(alpha = 0.8f),
            startAngle = -rotation * 1.4f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(center.x - midRadius, center.y - midRadius),
            size = Size(midRadius * 2f, midRadius * 2f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = SapphireBlue.copy(alpha = 0.7f),
            startAngle = -rotation * 1.4f + 190f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(center.x - midRadius, center.y - midRadius),
            size = Size(midRadius * 2f, midRadius * 2f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Core energy sphere
        val coreRadius = radius * 0.36f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    primaryGlow,
                    primaryGlow.copy(alpha = 0.25f)
                ),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )

        // Orbiting telemetry node
        val angleRad = Math.toRadians(rotation.toDouble())
        val nodeOffset = Offset(
            x = center.x + (outerRadius * cos(angleRad)).toFloat(),
            y = center.y + (outerRadius * sin(angleRad)).toFloat()
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = nodeOffset
        )
    }
}
