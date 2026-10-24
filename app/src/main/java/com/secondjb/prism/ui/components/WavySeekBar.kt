package com.secondjb.prism.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import com.secondjb.prism.ui.theme.adjustLuminance
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated sine-wave seekbar adapted from LastWave for Material 3 Expressive.
 * Features an undulating multi-depth fluid waveform when playing,
 * which flattens out smoothly to a sleek pill bar when paused.
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
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
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

    val effectivePrimary = primaryColor
    val effectiveSecondary = secondaryColor
    val effectiveTertiary = tertiaryColor

    val inactiveColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    }

    val textColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Tonal layers
    val layer1Color = effectiveTertiary.adjustLuminance(0.85f)
    val layer2Color = effectiveSecondary.adjustLuminance(1.10f)
    val layer3Color = effectivePrimary
    val thumbColor = effectivePrimary

    // Infinite wave phase transition
    val infiniteTransition = rememberInfiniteTransition(label = "PrismWaveAnimation")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase3"
    )

    val wavesActive = isPlaying && !dragging
    val currPhase1 = if (wavesActive) phase1 + 2.2f else 2.2f
    val currPhase2 = if (wavesActive) phase2 + 1.2f else 1.2f
    val currPhase3 = if (wavesActive) phase3 else 0f

    val density = LocalDensity.current
    val baseAmp1Px = with(density) { 10.0.dp.toPx() }
    val baseAmp2Px = with(density) { 7.5.dp.toPx() }
    val baseAmp3Px = with(density) { 5.5.dp.toPx() }
    val draggingAmpPx = with(density) { 1.0.dp.toPx() }

    val amp1 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp1Px,
        animationSpec = tween(durationMillis = 220),
        label = "Amp1"
    )
    val amp2 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp2Px,
        animationSpec = tween(durationMillis = 220),
        label = "Amp2"
    )
    val amp3 by animateFloatAsState(
        targetValue = if (dragging || !isPlaying) draggingAmpPx else baseAmp3Px,
        animationSpec = tween(durationMillis = 220),
        label = "Amp3"
    )

    val strokeWidth1 = with(density) { 3.dp.toPx() }
    val strokeWidth2 = with(density) { 4.5.dp.toPx() }
    val strokeWidth3 = with(density) { 6.dp.toPx() }
    val thumbRadiusPx = with(density) { 7.dp.toPx() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val playedWidth = width * shownFraction

                    // Inactive background track
                    val trackHeight = 4.dp.toPx()
                    drawRoundRect(
                        color = inactiveColor,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(width, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f)
                    )

                    if (playedWidth > 0f) {
                        // Clip path for played wave progress
                        val clipPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = 0f,
                                    top = 0f,
                                    right = playedWidth,
                                    bottom = height,
                                    cornerRadius = CornerRadius(trackHeight / 2f)
                                )
                            )
                        }

                        clipPath(clipPath) {
                            // Layer 1 Ambient wave
                            val path1 = Path()
                            val wavelength1 = 280f
                            val k1 = (2 * PI / wavelength1).toFloat()
                            path1.moveTo(0f, centerY + amp1 * sin(currPhase1))
                            var x = 0f
                            while (x <= width) {
                                val y = centerY + amp1 * sin(k1 * x + currPhase1)
                                path1.lineTo(x, y)
                                x += 4f
                            }
                            drawPath(
                                path = path1,
                                color = layer1Color.copy(alpha = 0.35f),
                                style = Stroke(width = strokeWidth1, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Layer 2 Middle wave
                            val path2 = Path()
                            val wavelength2 = 220f
                            val k2 = (2 * PI / wavelength2).toFloat()
                            path2.moveTo(0f, centerY + amp2 * sin(currPhase2))
                            x = 0f
                            while (x <= width) {
                                val y = centerY + amp2 * sin(k2 * x + currPhase2)
                                path2.lineTo(x, y)
                                x += 4f
                            }
                            drawPath(
                                path = path2,
                                color = layer2Color.copy(alpha = 0.55f),
                                style = Stroke(width = strokeWidth2, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Layer 3 Foreground wave
                            val path3 = Path()
                            val wavelength3 = 160f
                            val k3 = (2 * PI / wavelength3).toFloat()
                            path3.moveTo(0f, centerY + amp3 * sin(currPhase3))
                            x = 0f
                            while (x <= width) {
                                val y = centerY + amp3 * sin(k3 * x + currPhase3)
                                path3.lineTo(x, y)
                                x += 3f
                            }
                            drawPath(
                                path = path3,
                                color = layer3Color,
                                style = Stroke(width = strokeWidth3, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Thumb indicator
                        val thumbX = playedWidth.coerceIn(thumbRadiusPx, width - thumbRadiusPx)
                        drawCircle(
                            color = thumbColor.copy(alpha = 0.25f),
                            radius = thumbRadiusPx + 4.dp.toPx(),
                            center = Offset(thumbX, centerY)
                        )
                        drawCircle(
                            color = thumbColor,
                            radius = thumbRadiusPx,
                            center = Offset(thumbX, centerY)
                        )
                    }
                }

                // Interactive touch layer
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
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }

            if (showTimeLabels) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(shownMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                    Text(
                        text = if (boundedDurationMs > 0L) "-${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0L))}" else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%d:%02d".format(minutes, seconds)
}
