package com.dhanuk.govphoto_resizer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Safe progress widgets that do NOT use Material3 CircularProgressIndicator.
 * M3 indeterminate CircularProgressIndicator crashes on some BOM/animation
 * combinations with:
 * NoSuchMethodError KeyframesSpecConfig.at(...)
 */

@Composable
fun GovLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val h = size.height
        val w = size.width
        drawRoundRect(color = track, size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f))
        val pw = (w * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
        if (pw > 0f) {
            drawRoundRect(color = fill, size = Size(pw, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f))
        }
    }
}

@Composable
fun GovCircularProgress(
    modifier: Modifier = Modifier,
    size: Int = 24,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp
) {
    SafeCircularSpinner(
        modifier = modifier.size(size.dp),
        color = color,
        strokeWidth = strokeWidth
    )
}

@Composable
fun SafeCircularSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(diameter, diameter)
        rotate(degrees = angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
