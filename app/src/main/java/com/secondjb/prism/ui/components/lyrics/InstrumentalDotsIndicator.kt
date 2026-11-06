package com.secondjb.prism.ui.components.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
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
 * Calculates estimated end time of a lyric line based on syllable count or word count,
 * exactly matching Prism Desktop getLineEndSecs.
 */
fun getLineEndMs(line: LyricLine): Long {
    if (line.syllables.isNotEmpty()) {
        val lastSyl = line.syllables.last()
        return maxOf(line.timestampMs + 1000L, lastSyl.timeMs + lastSyl.durationMs)
    }
    val wordsCount = line.text.trim().split(Regex("""\s+""")).count { it.isNotEmpty() }
    val estimatedMs = maxOf(2000L, minOf(line.durationMs, (wordsCount * 550L)))
    return line.timestampMs + estimatedMs
}

/**
 * Computes interludes matching Prism Desktop LyricsView.tsx:
 * - Intro gap: at least 5.0 seconds (5000ms)
 * - Mid-song gap: true gap >= 5000ms OR (delta between starts >= 6000ms AND gap >= 4000ms)
 */
fun computeLyricInterludes(lines: List<LyricLine>): List<LyricInterlude> {
    if (lines.isEmpty()) return emptyList()
    val interludes = mutableListOf<LyricInterlude>()

    // Intro: only if intro is at least 5.0 seconds (5000ms)
    val firstLine = lines[0]
    if (firstLine.timestampMs >= 5000L) {
        interludes.add(
            LyricInterlude(
                key = "lyric-interlude-intro",
                startMs = 0L,
                endMs = firstLine.timestampMs,
                insertIndex = 0
            )
        )
    }

    // Mid-song: only actual long instrumental breaks
    for (i in 0 until lines.size - 1) {
        val curLine = lines[i]
        val nextLine = lines[i + 1]
        val lineEndMs = getLineEndMs(curLine)
        val gapMs = nextLine.timestampMs - lineEndMs
        val totalDeltaMs = nextLine.timestampMs - curLine.timestampMs

        if (gapMs >= 5000L || (totalDeltaMs >= 6000L && gapMs >= 4000L)) {
            interludes.add(
                LyricInterlude(
                    key = "lyric-interlude-$i",
                    startMs = lineEndMs,
                    endMs = nextLine.timestampMs,
                    insertIndex = i + 1
                )
            )
        }
    }
    return interludes
}

/**
 * Animated Instrumental Bubble Morphing Indicator.
 * Displays bubbles that float up and morph into musical notes and back to droplets.
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
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = onSeek != null) { onSeek?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        PixelPlayerBubbles(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Smooth Bubble-to-Musical-Note Morphing Animation.
 * 3 bubbles float smoothly upward in a staggered wave.
 * As each bubble rises, it blooms into a musical note at the crest of the float,
 * then gently dissolves back into a droplet as it descends.
 */
@Composable
fun PixelPlayerBubbles(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bubbles_transition")
    val animatedValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_animation_progress"
    )

    val density = LocalDensity.current
    val bubbleRadius = remember(density) { with(density) { 5.dp.toPx() } }

    val notePath = remember {
        val pathData = "M239.5,1.9c-4.6,1.1 -8.7,3.6 -12.2,7.3 -6.7,6.9 -6.3,-2.5 -6.3,151.9 0,76.9 -0.3,140 -0.7,140.2 -0.5,0.3 -4.2,-0.9 -8.3,-2.5 -48.1,-19.3 -102.8,-8.3 -138.6,27.7 -35.8,36.1 -41.4,85.7 -13.6,120.7 18.6,23.4 52.8,37.4 86.2,35.3 34.8,-2.1 65.8,-16 89.5,-39.9 14.5,-14.6 24.9,-31.9 30.7,-50.6l2.3,-7.5 0.2,-133c0.2,-73.2 0.5,-133.6 0.8,-134.2 0.8,-2.4 62,28.5 84.3,42.4 22.4,14.1 34.1,30.4 37.2,51.9 2.4,16.5 -2.2,34.5 -13,50.9 -6,9.1 -7,12.1 -4.8,14.3 2.2,2.2 5.3,1.2 13.8,-4.5 26.4,-17.9 45.6,-48 50,-78.2 1.9,-12.9 0.8,-34.3 -2.4,-46.1 -8.7,-31.7 -30.4,-58 -64.1,-77.8 -64.3,-37.9 -116,-67.3 -119.6,-68.1 -5,-1.2 -7.1,-1.2 -11.4,-0.2z"
        val path = PathParser().parsePathString(pathData).toPath()
        val bounds = path.getBounds()
        val maxDimension = max(bounds.width, bounds.height)
        val targetSize = with(density) { 14.dp.toPx() }
        val scaleFactor = if (maxDimension > 0f) targetSize / maxDimension else 1f

        val matrix = Matrix()
        matrix.translate(x = -bounds.left, y = -bounds.top)
        matrix.scale(x = scaleFactor, y = scaleFactor)
        val finalWidth = bounds.width * scaleFactor
        val finalHeight = bounds.height * scaleFactor
        matrix.translate(x = -finalWidth / 2f, y = -finalHeight / 2f)

        Path().apply {
            addPath(path)
            transform(matrix)
        }
    }

    Canvas(modifier = modifier.size(88.dp, 48.dp)) {
        val bubbleCount = 3
        val baseY = size.height / 2f

        for (i in 0 until bubbleCount) {
            val progress = (animatedValue + i * (1f / bubbleCount)) % 1f
            // Smooth vertical floating: rises at center (progress = 0.5), settles at ends
            val morphFactor = sin(progress * PI).toFloat()
            val yOffset = -morphFactor * 13.dp.toPx()
            val xWobble = sin(progress * 2 * PI).toFloat() * 2.5.dp.toPx()

            val columnX = (size.width / (bubbleCount + 1)) * (i + 1) + xWobble
            val currentY = baseY + yOffset

            // 1. Circle bubble component (fades out as it morphs into note)
            val circleAlpha = (1f - 0.75f * morphFactor).coerceIn(0f, 1f)
            if (circleAlpha > 0.02f) {
                drawCircle(
                    color = color.copy(alpha = 0.85f * circleAlpha),
                    radius = bubbleRadius * (1f + 0.15f * (1f - morphFactor)),
                    center = Offset(columnX, currentY)
                )
            }

            // 2. Musical note component (fades in and blooms at the crest of the float)
            val noteAlpha = morphFactor.coerceIn(0f, 1f)
            if (noteAlpha > 0.02f) {
                val noteScale = 0.7f + 0.45f * morphFactor
                translate(left = columnX, top = currentY) {
                    scale(scale = noteScale, pivot = Offset.Zero) {
                        drawPath(
                            path = notePath,
                            color = color.copy(alpha = 0.95f * noteAlpha)
                        )
                    }
                }
            }
        }
    }
}
