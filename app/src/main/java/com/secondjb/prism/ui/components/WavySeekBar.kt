package com.secondjb.prism.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var h = 0f
    val s = if (max == 0f) 0f else delta / max
    val v = max
    if (delta > 0.00001f) {
        h = when (max) {
            r -> (g - b) / delta + (if (g < b) 6f else 0f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
    }
    return floatArrayOf(h, s, v)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f)
    )
}

/**
 * Shifts the lightness and saturation of a Compose Color to produce
 * rich, dynamic tonal variations within the theme palette.
 */
private fun Color.shiftTonal(lightnessDelta: Float, saturationScale: Float = 1.0f): Color {
    val hsv = rgbToHsv(red, green, blue)
    val originalSaturation = hsv[1]
    hsv[1] = (hsv[1] * saturationScale).coerceIn(if (originalSaturation > 0.05f) 0.15f else 0.0f, 1.0f)
    hsv[2] = (hsv[2] + lightnessDelta).coerceIn(if (originalSaturation > 0.05f) 0.15f else 0.0f, 1.0f)
    return hsvToRgb(hsv[0], hsv[1], hsv[2])
}

/**
 * Ensures a color has sufficient brightness and saturation to stand out
 * vibrantly on dark scrims and backgrounds without washing out.
 */
private fun Color.ensureLuminousForDarkBackground(minBrightness: Float = 0.55f): Color {
    val hsv = rgbToHsv(red, green, blue)
    if (hsv[2] < minBrightness) {
        hsv[2] = minBrightness
    }
    if (hsv[1] in 0.05f..0.35f) {
        hsv[1] = 0.35f
    }
    return hsvToRgb(hsv[0], hsv[1], hsv[2])
}

private fun formatTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}

/**
 * Material Design 3 Dynamic Counter-Gradient Frosted Glass Waveform Seekbar (Full LastWave Implementation).
 * Features 3 frosted glass waves that dynamically transition across playback progress:
 * - Layer 1: Soft organic wave
 * - Layer 2: Medium harmonic wave
 * - Layer 3: Vibrant primary wave with dynamic envelope
 */
@Composable
fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isTranslucent: Boolean = false,
    trackKey: String? = null,
    showTimeLabels: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val interactionSource = remember(trackKey) { MutableInteractionSource() }
    var isInteracting by remember(trackKey) { mutableStateOf(false) }
    LaunchedEffect(interactionSource, trackKey) {
        var dragCount = 0
        var pressCount = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragCount++
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount = maxOf(0, dragCount - 1)
                is PressInteraction.Press -> pressCount++
                is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = maxOf(0, pressCount - 1)
            }
            isInteracting = dragCount > 0 || pressCount > 0
        }
    }

    var dragPositionMs by remember(trackKey) { mutableStateOf<Float?>(null) }
    var lastSeekValue by remember(trackKey) { mutableStateOf<Float?>(null) }
    var showRemainingTime by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(isInteracting, trackKey) {
        if (!isInteracting) {
            delay(120L)
            dragPositionMs = null
            lastSeekValue = null
        }
    }

    LaunchedEffect(dragPositionMs, isInteracting, trackKey) {
        if (dragPositionMs != null && !isInteracting) {
            delay(250L)
            dragPositionMs = null
            lastSeekValue = null
        }
    }
    val dragging = isInteracting || dragPositionMs != null

    val boundedDurationMs = durationMs.coerceAtLeast(0L)
    val boundedPositionMs = if (boundedDurationMs > 0L) {
        positionMs.coerceIn(0L, boundedDurationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val shownMs = if (boundedDurationMs > 0L) {
        (dragPositionMs?.toLong() ?: boundedPositionMs).coerceIn(0L, boundedDurationMs)
    } else {
        (dragPositionMs?.toLong() ?: boundedPositionMs).coerceAtLeast(0L)
    }
    val shownFraction = if (boundedDurationMs > 0L) {
        (shownMs.toDouble() / boundedDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    // Ensure colors stand out vibrantly on dark scrim/translucent backgrounds
    val effectivePrimary = remember(primaryColor, isTranslucent) {
        if (isTranslucent) primaryColor.ensureLuminousForDarkBackground(0.60f) else primaryColor
    }
    val effectiveSecondary = remember(secondaryColor, isTranslucent) {
        if (isTranslucent) secondaryColor.ensureLuminousForDarkBackground(0.52f) else secondaryColor
    }
    val effectiveTertiary = remember(tertiaryColor, isTranslucent) {
        if (isTranslucent) tertiaryColor.ensureLuminousForDarkBackground(0.48f) else tertiaryColor
    }

    val inactiveColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    }
    val textColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Pre-calculated tonal palette (zero allocation in draw loop)
    val layer1Light = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = +0.18f, saturationScale = 0.85f) }
    val layer1Dark = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = -0.15f, saturationScale = 1.30f) }
    val layer2Dark = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = -0.16f, saturationScale = 1.30f) }
    val layer2Light = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = +0.18f, saturationScale = 0.85f) }
    val layer3Light = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = +0.20f, saturationScale = 0.90f) }
    val layer3Dark = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = -0.14f, saturationScale = 1.35f) }
    val thumbColor = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = -0.06f, saturationScale = 1.15f) }

    // Fluid wave animations
    val infiniteTransition = rememberInfiniteTransition(label = "MaterialGlassWaveAnimation")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Phase1",
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Phase2",
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Phase3",
    )

    val wavesActive = isPlaying && !dragging
    val currPhase1 = if (wavesActive) phase1 + 2.2f else 2.2f
    val currPhase2 = if (wavesActive) phase2 + 1.2f else 1.2f
    val currPhase3 = if (wavesActive) phase3 else 0f

    val density = LocalDensity.current
    val baseAmp1Px = with(density) { 13.0.dp.toPx() }
    val baseAmp2Px = with(density) { 10.0.dp.toPx() }
    val baseAmp3Px = with(density) { 7.5.dp.toPx() }
    val draggingAmpPx = with(density) { 1.2.dp.toPx() }

    val amp1 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp1Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp1",
    )
    val amp2 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp2Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp2",
    )
    val amp3 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp3Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp3",
    )

    val waveLength1Px = with(density) { 160.dp.toPx() }
    val waveLength2Px = with(density) { 125.dp.toPx() }
    val waveLength3Px = with(density) { 95.dp.toPx() }

    val baseTrackThicknessPx = with(density) { 4.5.dp.toPx() }
    val thumbRadiusPx = with(density) { 7.5.dp.toPx() }
    val transitionLengthPx = with(density) { 44.dp.toPx() }
    val waveSampleStepPx = with(density) { 1.5.dp.toPx() }

    val pathFilled1 = remember { Path() }
    val pathContour1 = remember { Path() }
    val pathFilled2 = remember { Path() }
    val pathContour2 = remember { Path() }
    val pathFilled3 = remember { Path() }
    val pathContour3 = remember { Path() }
    val clipPathBounds = remember { Path() }

    Column(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f + 7.dp.toPx()
                    val thumbX = (shownFraction * width).coerceIn(0f, width)
                    val halfThickness = baseTrackThicknessPx / 2f
                    val bottomY = centerY + halfThickness
                    val topBaselineY = centerY - halfThickness

                    // 1. Inactive background track
                    drawLine(
                        color = inactiveColor,
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = baseTrackThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    // 2. Active 3-Layer Waves with Smooth Envelope Transition
                    if (thumbX > 0f) {
                        clipPathBounds.reset()
                        clipPathBounds.addRoundRect(
                            RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(
                                    left = -halfThickness,
                                    top = 0f,
                                    right = thumbX + halfThickness,
                                    bottom = height,
                                ),
                                topLeft = CornerRadius(halfThickness, halfThickness),
                                bottomLeft = CornerRadius(halfThickness, halfThickness),
                                topRight = CornerRadius(halfThickness, halfThickness),
                                bottomRight = CornerRadius(halfThickness, halfThickness),
                            )
                        )

                        clipPath(clipPathBounds) {
                            fun smootherstep(t: Float): Float {
                                val c = t.coerceIn(0f, 1f)
                                return c * c * c * (c * (c * 6f - 15f) + 10f)
                            }

                            fun populateWave(
                                filled: Path,
                                contour: Path,
                                wavelength: Float,
                                amplitude: Float,
                                phase: Float,
                            ) {
                                filled.reset()
                                contour.reset()
                                filled.moveTo(0f, bottomY)
                                filled.lineTo(0f, topBaselineY)
                                contour.moveTo(0f, topBaselineY)

                                var x = 0f
                                val effectiveTransition = minOf(transitionLengthPx, thumbX * 0.48f)
                                val invTransition = if (effectiveTransition > 0f) 1f / effectiveTransition else 0f
                                val invWavelength2Pi = (2 * PI / wavelength).toFloat()
                                while (x < thumbX) {
                                    val startEnv = if (invTransition > 0f) smootherstep(x * invTransition) else 1f
                                    val endEnv = if (invTransition > 0f) smootherstep((thumbX - x) * invTransition) else 1f
                                    val envelope = startEnv * endEnv

                                    val angle = x * invWavelength2Pi - phase
                                    val waveHeight = (0.5f + 0.5f * sin(angle)) * amplitude * envelope
                                    val y = topBaselineY - waveHeight

                                    filled.lineTo(x, y)
                                    contour.lineTo(x, y)
                                    x += waveSampleStepPx
                                }
                                val endY = topBaselineY
                                filled.lineTo(thumbX, endY)
                                contour.lineTo(thumbX, endY)
                                filled.lineTo(thumbX, bottomY)
                                filled.close()
                            }

                            populateWave(pathFilled1, pathContour1, waveLength1Px, amp1, currPhase1)
                            populateWave(pathFilled2, pathContour2, waveLength2Px, amp2, currPhase2)
                            populateWave(pathFilled3, pathContour3, waveLength3Px, amp3, currPhase3)

                            val activeWidth = thumbX.coerceAtLeast(1f)

                            // Layer 1
                            drawPath(
                                path = pathContour1,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        layer1Light.copy(alpha = if (isTranslucent) 0.42f else 0.38f),
                                        layer1Dark.copy(alpha = if (isTranslucent) 0.32f else 0.28f),
                                    ),
                                    startX = 0f,
                                    endX = activeWidth,
                                ),
                                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )

                            // Layer 2
                            drawPath(
                                path = pathContour2,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        layer2Dark.copy(alpha = if (isTranslucent) 0.72f else 0.66f),
                                        layer2Light.copy(alpha = if (isTranslucent) 0.62f else 0.56f),
                                    ),
                                    startX = 0f,
                                    endX = activeWidth,
                                ),
                                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )

                            // Layer 3
                            drawPath(
                                path = pathContour3,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        layer3Light.copy(alpha = if (isTranslucent) 0.98f else 0.95f),
                                        layer3Dark.copy(alpha = if (isTranslucent) 0.92f else 0.88f),
                                    ),
                                    startX = 0f,
                                    endX = activeWidth,
                                ),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )

                            // Crisp Baseline Bar
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(layer3Light, layer3Dark),
                                    startX = 0f,
                                    endX = activeWidth,
                                ),
                                start = Offset(0f, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = baseTrackThicknessPx,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // Thumb Indicator
                    if (boundedDurationMs > 0) {
                        drawCircle(
                            color = thumbColor.copy(alpha = 0.28f),
                            radius = thumbRadiusPx + 3.dp.toPx(),
                            center = Offset(thumbX, centerY),
                        )
                        drawCircle(
                            color = thumbColor,
                            radius = thumbRadiusPx,
                            center = Offset(thumbX, centerY),
                        )
                    }
                }

                Slider(
                    value = if (boundedDurationMs > 0L) shownMs.toFloat().coerceIn(0f, boundedDurationMs.toFloat()) else 0f,
                    onValueChange = {
                        dragPositionMs = it
                        lastSeekValue = it
                    },
                    onValueChangeFinished = {
                        if (boundedDurationMs <= 0L) {
                            dragPositionMs = null
                            lastSeekValue = null
                            return@Slider
                        }
                        val target = (lastSeekValue ?: dragPositionMs)?.toLong()?.coerceIn(0L, boundedDurationMs)
                        dragPositionMs = null
                        lastSeekValue = null
                        if (target != null) onSeek(target)
                    },
                    valueRange = 0f..boundedDurationMs.coerceAtLeast(1L).toFloat(),
                    enabled = boundedDurationMs > 0L,
                    interactionSource = interactionSource,
                    modifier = Modifier.matchParentSize(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledThumbColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                    ),
                )
            }

            if (showTimeLabels) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(shownMs),
                        style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                        color = textColor,
                    )
                    Text(
                        text = if (boundedDurationMs > 0L) {
                            if (showRemainingTime) {
                                "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0))}"
                            } else {
                                formatTime(boundedDurationMs)
                            }
                        } else "--:--",
                        style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                        color = textColor,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
            }
        }
    }
}
