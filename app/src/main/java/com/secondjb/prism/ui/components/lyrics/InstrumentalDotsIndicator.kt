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
import androidx.compose.ui.graphics.vector.PathNode
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
 * Detects if a lyric line is an instrumental indicator or note symbol (matching Prism Desktop).
 */
fun hasMusicNoteOrInstrumental(text: String): Boolean {
    if (text.isBlank()) return false
    val trimmed = text.trim()
    if (Regex("""[♪♫]""").containsMatchIn(trimmed)) return true
    if (Regex("""^\[?\s*(instrumental|solo|music|outro|intro|guitar solo|piano solo)\s*]?$""", RegexOption.IGNORE_CASE).matches(trimmed)) return true
    return false
}

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
    val lineDurSecs = (line.durationMs / 1000.0).coerceAtLeast(1.2)
    val estimatedSecs = maxOf(2.0, minOf(lineDurSecs, wordsCount * 0.55))
    return line.timestampMs + (estimatedSecs * 1000.0).toLong()
}

/**
 * Computes interludes matching Prism Desktop LyricsView.tsx identically:
 * - Intro gap: at least 5.0 seconds (5000ms) and not marked with music note / instrumental tag
 * - Mid-song gap: true gap >= 5000ms OR (delta between starts >= 6000ms AND gap >= 4000ms)
 *   and neither boundary line is a music note / instrumental marker
 */
fun computeLyricInterludes(lines: List<LyricLine>): List<LyricInterlude> {
    if (lines.isEmpty()) return emptyList()
    val interludes = mutableListOf<LyricInterlude>()

    // Intro: only if intro is at least 5.0 seconds (5000ms)
    val firstLine = lines[0]
    if (firstLine.timestampMs >= 5000L && !hasMusicNoteOrInstrumental(firstLine.text)) {
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

        if ((gapMs >= 5000L || (totalDeltaMs >= 6000L && gapMs >= 4000L)) &&
            !hasMusicNoteOrInstrumental(curLine.text) &&
            !hasMusicNoteOrInstrumental(nextLine.text)
        ) {
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
 * PixelPlayer-Inspired Animated Instrumental Bubble Morphing Indicator.
 * Displays bubbles that morph into musical notes as they rise and back to circles as they fall.
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
            .height(48.dp)
            .clickable(enabled = onSeek != null) { onSeek?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        PixelPlayerBubbles(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * PixelPlayer Exact Composable:
 * Displays a row of animated bubbles that morph into musical notes as they rise and back to circles as they fall.
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
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_animation_progress"
    )

    val density = LocalDensity.current
    val bubbleRadius = remember(density) { with(density) { 4.dp.toPx() } }

    val (morphableCircle, morphableNote) = remember(bubbleRadius) {
        val circleNodes = createCirclePathNodes(radius = bubbleRadius)
        val noteNodes = createVectorNotePathNodes(targetSize = bubbleRadius * 2.5f)

        makePathsCompatible(circleNodes, noteNodes)
        circleNodes to noteNodes
    }

    Canvas(modifier = modifier.size(64.dp, 48.dp)) {
        val bubbleCount = 3
        val bubbleColor = color.copy(alpha = 0.7f)

        for (i in 0 until bubbleCount) {
            val progress = (animatedValue + i * (1f / bubbleCount)) % 1f
            val yOffset = sin(progress * 2 * PI).toFloat() * 8.dp.toPx()

            val morphProgress = when {
                progress in 0f..0.25f -> progress / 0.25f
                progress in 0.25f..0.5f -> 1.0f - (progress - 0.25f) / 0.25f
                else -> 0f
            }.coerceIn(0f, 1f)

            // Scale animation
            val scale = lerpFloat(1.0f, 1.4f, morphProgress)

            // Dynamic horizontal offset that activates with morphing
            val xOffsetCorrection = lerpFloat(0f, bubbleRadius * 1.8f, morphProgress)

            val morphedPath = lerpPath(
                start = morphableCircle,
                stop = morphableNote,
                fraction = morphProgress
            ).toPath()

            // Position the animation container in its column
            translate(left = (size.width / (bubbleCount + 1)) * (i + 1)) {
                // Apply vertical offset (wave) and horizontal correction
                val drawOffset = Offset(x = xOffsetCorrection, y = size.height / 2 + yOffset)

                translate(left = drawOffset.x, top = drawOffset.y) {
                    scale(scale = scale, pivot = Offset.Zero) {
                        drawPath(
                            path = morphedPath,
                            color = bubbleColor
                        )
                    }
                }
            }
        }
    }
}

// --- PixelPlayer Exact Path Morphing Helpers ---

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

private fun lerpPath(start: List<PathNode>, stop: List<PathNode>, fraction: Float): List<PathNode> {
    return start.mapIndexed { index, startNode ->
        val stopNode = stop[index]
        when (startNode) {
            is PathNode.MoveTo -> {
                val stopMoveTo = stopNode as PathNode.MoveTo
                PathNode.MoveTo(
                    lerpFloat(startNode.x, stopMoveTo.x, fraction),
                    lerpFloat(startNode.y, stopMoveTo.y, fraction)
                )
            }
            is PathNode.CurveTo -> {
                val stopCurveTo = stopNode as PathNode.CurveTo
                PathNode.CurveTo(
                    lerpFloat(startNode.x1, stopCurveTo.x1, fraction),
                    lerpFloat(startNode.y1, stopCurveTo.y1, fraction),
                    lerpFloat(startNode.x2, stopCurveTo.x2, fraction),
                    lerpFloat(startNode.y2, stopCurveTo.y2, fraction),
                    lerpFloat(startNode.x3, stopCurveTo.x3, fraction),
                    lerpFloat(startNode.y3, stopCurveTo.y3, fraction)
                )
            }
            else -> startNode
        }
    }
}

private fun makePathsCompatible(nodes1: MutableList<PathNode>, nodes2: MutableList<PathNode>): Pair<MutableList<PathNode>, MutableList<PathNode>> {
    while (nodes1.size < nodes2.size) {
        nodes1.add(nodes1.size - 1, nodes1[nodes1.size - 2])
    }
    while (nodes2.size < nodes1.size) {
        nodes2.add(nodes2.size - 1, nodes2[nodes2.size - 2])
    }
    return nodes1 to nodes2
}

private fun createVectorNotePathNodes(targetSize: Float): MutableList<PathNode> {
    val pathData = "M239.5,1.9c-4.6,1.1 -8.7,3.6 -12.2,7.3 -6.7,6.9 -6.3,-2.5 -6.3,151.9 0,76.9 -0.3,140 -0.7,140.2 -0.5,0.3 -4.2,-0.9 -8.3,-2.5 -48.1,-19.3 -102.8,-8.3 -138.6,27.7 -35.8,36.1 -41.4,85.7 -13.6,120.7 18.6,23.4 52.8,37.4 86.2,35.3 34.8,-2.1 65.8,-16 89.5,-39.9 14.5,-14.6 24.9,-31.9 30.7,-50.6l2.3,-7.5 0.2,-133c0.2,-73.2 0.5,-133.6 0.8,-134.2 0.8,-2.4 62,28.5 84.3,42.4 22.4,14.1 34.1,30.4 37.2,51.9 2.4,16.5 -2.2,34.5 -13,50.9 -6,9.1 -7,12.1 -4.8,14.3 2.2,2.2 5.3,1.2 13.8,-4.5 26.4,-17.9 45.6,-48 50,-78.2 1.9,-12.9 0.8,-34.3 -2.4,-46.1 -8.7,-31.7 -30.4,-58 -64.1,-77.8 -64.3,-37.9 -116,-67.3 -119.6,-68.1 -5,-1.2 -7.1,-1.2 -11.4,-0.2z"
    val parser = PathParser().parsePathString(pathData)

    val groupScale = 0.253f
    val bounds = Path().apply { parser.toPath(this) }.getBounds()
    val maxDimension = max(bounds.width, bounds.height)
    val scale = if (maxDimension > 0f) targetSize / (maxDimension * groupScale) else 1f

    val matrix = Matrix()
    matrix.translate(x = -bounds.left, y = -bounds.top)
    matrix.scale(x = groupScale * scale, y = groupScale * scale)
    val finalWidth = bounds.width * groupScale * scale
    val finalHeight = bounds.height * groupScale * scale

    matrix.translate(x = -finalWidth / 2f, y = -finalHeight / 2f)

    return parser.toNodes().toAbsolute().transform(matrix).toCurvesOnly()
}

private fun createCirclePathNodes(radius: Float): MutableList<PathNode> {
    val kappa = 0.552284749831f
    val rk = radius * kappa
    return mutableListOf(
        PathNode.MoveTo(0f, -radius),
        PathNode.CurveTo(rk, -radius, radius, -rk, radius, 0f),
        PathNode.CurveTo(radius, rk, rk, radius, 0f, radius),
        PathNode.CurveTo(-rk, radius, -radius, rk, -radius, 0f),
        PathNode.CurveTo(-radius, -rk, -rk, -radius, 0f, -radius),
        PathNode.Close
    )
}

private fun List<PathNode>.toAbsolute(): MutableList<PathNode> {
    val absoluteNodes = mutableListOf<PathNode>()
    var currentX = 0f
    var currentY = 0f
    this.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeMoveTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.MoveTo(currentX, currentY)) }
            is PathNode.LineTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeLineTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.LineTo(currentX, currentY)) }
            is PathNode.CurveTo -> { currentX = node.x3; currentY = node.y3; absoluteNodes.add(node) }
            is PathNode.RelativeCurveTo -> {
                absoluteNodes.add(PathNode.CurveTo(currentX + node.dx1, currentY + node.dy1, currentX + node.dx2, currentY + node.dy2, currentX + node.dx3, currentY + node.dy3))
                currentX += node.dx3; currentY += node.dy3
            }
            is PathNode.Close -> absoluteNodes.add(node)
            else -> {}
        }
    }
    return absoluteNodes
}

private fun MutableList<PathNode>.toCurvesOnly(): MutableList<PathNode> {
    val curveNodes = mutableListOf<PathNode>()
    var lastX = 0f
    var lastY = 0f

    this.forEach { node ->
        when(node) {
            is PathNode.MoveTo -> { curveNodes.add(node); lastX = node.x; lastY = node.y }
            is PathNode.LineTo -> { curveNodes.add(PathNode.CurveTo(lastX, lastY, node.x, node.y, node.x, node.y)); lastX = node.x; lastY = node.y }
            is PathNode.CurveTo -> { curveNodes.add(node); lastX = node.x3; lastY = node.y3 }
            is PathNode.Close -> curveNodes.add(node)
            else -> {}
        }
    }
    return curveNodes
}

private fun List<PathNode>.transform(matrix: Matrix): MutableList<PathNode> {
    return this.map { node ->
        when (node) {
            is PathNode.MoveTo -> {
                val p = matrix.map(Offset(node.x, node.y))
                PathNode.MoveTo(p.x, p.y)
            }
            is PathNode.LineTo -> {
                val p = matrix.map(Offset(node.x, node.y))
                PathNode.LineTo(p.x, p.y)
            }
            is PathNode.CurveTo -> {
                val p1 = matrix.map(Offset(node.x1, node.y1))
                val p2 = matrix.map(Offset(node.x2, node.y2))
                val p3 = matrix.map(Offset(node.x3, node.y3))
                PathNode.CurveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            else -> node
        }
    }.toMutableList()
}

private fun List<PathNode>.toPath(): Path {
    val path = Path()
    this.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> path.moveTo(node.x, node.y)
            is PathNode.LineTo -> path.lineTo(node.x, node.y)
            is PathNode.CurveTo -> path.cubicTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
            is PathNode.Close -> path.close()
            else -> {}
        }
    }
    return path
}
