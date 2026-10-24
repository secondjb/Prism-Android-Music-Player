package com.secondjb.prism.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.secondjb.prism.ui.theme.rememberAnimatedPrismGradient

/**
 * Native vector rendering of the Prism refracting logo (matching Windows GeminiLogo SVG).
 *
 * Left wave: Solid silver incoming wave.
 * Central triangle: Grounded equilateral-style prism.
 * Right wave: Dynamic refracted sine wave themed by current album art color stops.
 */
@Composable
fun PrismLogo(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = rememberAnimatedPrismGradient(),
    prismColor: Color = MaterialTheme.colorScheme.onBackground,
    inputWaveColor: Color = Color(0xFFA1A1AA)
) {
    Canvas(modifier = modifier) {
        val viewBoxWidth = 260f
        val viewBoxHeight = 120f
        val scaleX = size.width / viewBoxWidth
        val scaleY = size.height / viewBoxHeight
        val scale = minOf(scaleX, scaleY)
        val offsetX = (size.width - viewBoxWidth * scale) / 2f
        val offsetY = (size.height - viewBoxHeight * scale) / 2f

        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            translate(left = -120f, top = -40f)
        }) {
            val strokeWidth = 10f
            val stroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // Left Input Sine Wave (Solid Silver, Shortened)
            // d="M 131.15 100 q 15 -35, 30 0 t 30 0 t 30 0"
            val leftWavePath = Path().apply {
                moveTo(131.15f, 100f)
                quadraticTo(146.15f, 65f, 161.15f, 100f)
                quadraticTo(176.15f, 135f, 191.15f, 100f)
                quadraticTo(206.15f, 65f, 221.15f, 100f)
            }
            drawPath(
                path = leftWavePath,
                color = inputWaveColor,
                style = stroke
            )

            // Right Output Sine Wave (Vibrant Rainbow / Dynamic Album Art Gradient)
            // d="M 278.85 100 q 15 -35, 30 0 t 30 0 t 30 0"
            val rightWavePath = Path().apply {
                moveTo(278.85f, 100f)
                quadraticTo(293.85f, 65f, 308.85f, 100f)
                quadraticTo(323.85f, 135f, 338.85f, 100f)
                quadraticTo(353.85f, 65f, 368.85f, 100f)
            }
            drawPath(
                path = rightWavePath,
                brush = Brush.horizontalGradient(
                    colors = gradientColors,
                    startX = 278.85f,
                    endX = 368.85f
                ),
                style = stroke
            )

            // Central Grounded Prism (Crisp White / Dark Charcoal depending on theme)
            // points="250,50 192.3,150 307.7,150"
            val prismPath = Path().apply {
                moveTo(250f, 50f)
                lineTo(192.3f, 150f)
                lineTo(307.7f, 150f)
                close()
            }
            drawPath(
                path = prismPath,
                color = prismColor,
                style = stroke
            )
        }
    }
}
