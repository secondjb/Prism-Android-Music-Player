package com.secondjb.prism.ui.components.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Data structure representing an instrumental gap / interlude in synced lyrics.
 */
data class LyricInterlude(
    val key: String,
    val startMs: Long,
    val endMs: Long,
    val insertIndex: Int
)

/**
 * Computes interludes:
 * - Intro interlude if first lyric line begins >= 4.0s
 * - Mid-song interlude if gap between consecutive lines >= 4.0s
 */
fun computeLyricInterludes(lines: List<LyricLine>): List<LyricInterlude> {
    if (lines.isEmpty()) return emptyList()
    val interludes = mutableListOf<LyricInterlude>()

    val firstLine = lines[0]
    if (firstLine.timestampMs >= 4000L) {
        interludes.add(
            LyricInterlude(
                key = "lyric-interlude-intro",
                startMs = 0L,
                endMs = firstLine.timestampMs,
                insertIndex = 0
            )
        )
    }

    for (i in 0 until lines.size - 1) {
        val curLine = lines[i]
        val nextLine = lines[i + 1]
        val curEndMs = if (curLine.hasExplicitSyllables && curLine.syllables.isNotEmpty()) {
            val lastSyl = curLine.syllables.last()
            (lastSyl.timeMs + lastSyl.durationMs).coerceAtLeast(curLine.timestampMs + 800L)
        } else {
            curLine.timestampMs + 2000L
        }
        val gap = nextLine.timestampMs - curEndMs
        if (gap >= 4000L && nextLine.timestampMs - curLine.timestampMs >= 5000L) {
            interludes.add(
                LyricInterlude(
                    key = "lyric-interlude-$i",
                    startMs = curEndMs,
                    endMs = nextLine.timestampMs,
                    insertIndex = i + 1
                )
            )
        }
    }
    return interludes
}

/**
 * Material 3 Expressive Instrumental 3-Dot Indicator:
 * Mirrors Prism Desktop InterludeIndicator and PixelPlayer:
 * 1. Ambient Phase (> 3.0s remaining or continuous instrumental): Staggered rhythmic wave bounce.
 * 2. Countdown Phase (last 3.0s -> 0.0s): Three sequential jumping balls (3 -> 2 -> 1) landing right as vocals start.
 * 3. Inactive Phase: Subtle muted row (··· ♪ ···) with tap-to-seek.
 */
@Composable
fun InstrumentalDotsIndicator(
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isActive: Boolean,
    isContinuous: Boolean = false,
    onSeek: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isActive && !isContinuous) {
        // Inactive State: Subtle clickable interlude marker
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { onSeek?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(0.28f)
            ) {
                Text(
                    text = "···",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "···",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val totalGapMs = (endMs - startMs).coerceAtLeast(100L)
    val countdownDurationMs = minOf(3000L, totalGapMs)
    val countdownStartMs = endMs - countdownDurationMs
    val timeRemainingMs = (endMs - currentPositionMs).coerceAtLeast(0L)

    val isCountdown = !isContinuous && (timeRemainingMs in 0L..countdownDurationMs)
    val isAmbient = isContinuous || !isCountdown

    // Continuous or Ambient Infinite Wave Animation
    val infiniteTransition = rememberInfiniteTransition(label = "ambientWave")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientWaveProgress"
    )

    // Primary gradient colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.primaryContainer

    val dotSize = 14.dp
    val jumpHeightPx = 48f // ~18dp in pixel terms

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = onSeek != null) { onSeek?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        if (isCountdown) {
            // Final 3-2-1 countdown jumping balls
            val clampedTime = currentPositionMs.coerceIn(countdownStartMs, endMs)
            val p = ((clampedTime - countdownStartMs).toFloat() / countdownDurationMs.toFloat()).coerceIn(0f, 1f)

            // Ball 1 (3.0s -> 2.0s: p from 0 to 1/3)
            var y1 = 0f
            var scale1 = 0.95f
            var alpha1 = 0.35f
            if (p in 0f..(1f / 3f)) {
                val p1 = p * 3f
                y1 = -sin(p1 * PI.toFloat()) * jumpHeightPx
                scale1 = 1.30f
                alpha1 = 1.0f
            } else if (p > 1f / 3f) {
                scale1 = 1.05f
                alpha1 = 0.85f
            }

            // Ball 2 (2.0s -> 1.0s: p from 1/3 to 2/3)
            var y2 = 0f
            var scale2 = 0.95f
            var alpha2 = 0.35f
            if (p in (1f / 3f)..(2f / 3f)) {
                val p2 = (p - 1f / 3f) * 3f
                y2 = -sin(p2 * PI.toFloat()) * jumpHeightPx
                scale2 = 1.30f
                alpha2 = 1.0f
            } else if (p > 2f / 3f) {
                scale2 = 1.05f
                alpha2 = 0.85f
            }

            // Ball 3 (1.0s -> 0.0s: p from 2/3 to 1.0)
            var y3 = 0f
            var scale3 = 0.95f
            var alpha3 = 0.35f
            if (p in (2f / 3f)..1.0f) {
                val p3 = (p - 2f / 3f) * 3f
                y3 = -sin(p3 * PI.toFloat()) * jumpHeightPx
                scale3 = 1.30f
                alpha3 = 1.0f
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ball 1
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, y1.toInt()) }
                        .scale(scale1)
                        .alpha(alpha1)
                        .size(dotSize)
                        .background(
                            brush = Brush.radialGradient(listOf(Color.White, primaryColor)),
                            shape = CircleShape
                        )
                )

                // Ball 2
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, y2.toInt()) }
                        .scale(scale2)
                        .alpha(alpha2)
                        .size(dotSize)
                        .background(
                            brush = Brush.radialGradient(listOf(Color.White, primaryColor)),
                            shape = CircleShape
                        )
                )

                // Ball 3
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, y3.toInt()) }
                        .scale(scale3)
                        .alpha(alpha3)
                        .size(dotSize)
                        .background(
                            brush = Brush.radialGradient(listOf(Color.White, primaryColor)),
                            shape = CircleShape
                        )
                )
            }
        } else {
            // Ambient Phase: Smooth rhythmic dancing wave
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val effectivePlaying = isPlaying || isContinuous
                listOf(0f, 0.22f, 0.44f).forEach { offset ->
                    val dotP = if (effectivePlaying) ((waveProgress + offset) % 1f) else 0f
                    val bounce = if (dotP < 0.5f) sin(dotP * 2f * PI.toFloat()) else 0f
                    val y = -bounce * (jumpHeightPx * 0.45f)
                    val scale = 0.95f + 0.25f * bounce
                    val alpha = 0.45f + 0.55f * bounce

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, y.toInt()) }
                            .scale(scale)
                            .alpha(alpha)
                            .size(dotSize)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, primaryColor)
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
