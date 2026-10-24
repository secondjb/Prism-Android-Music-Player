package com.secondjb.prism.ui.components.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive synchronized lyrics preview card for NowPlayingScreen.
 * Features centered typography, word-by-word syllable pacing, and seamless viewport centering.
 */
@Composable
fun ModernLyricsView(
    lyricsText: String?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {}
) {
    if (lyricsText.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No lyrics available for this track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        return
    }

    val parsedLines = remember(lyricsText) { LrcParser.parse(lyricsText) }
    val activeIndex by remember(currentPositionMs, parsedLines) {
        derivedStateOf {
            val idx = parsedLines.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx == -1 && parsedLines.isNotEmpty()) 0 else idx
        }
    }

    val listState = rememberLazyListState()

    // Keep active lyric line centered in the preview card viewport
    LaunchedEffect(activeIndex) {
        if (activeIndex in parsedLines.indices) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(index = targetScroll, scrollOffset = -80)
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics Header with Tap-to-Fullscreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleFullScreen)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle Fullscreen Lyrics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Lyrics Scroll List with Centered Alignment & Viewport Balancing
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 64.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(parsedLines) { index, line ->
                    val isActive = index == activeIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(durationMillis = 200),
                        label = "lyricTextColor"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.38f,
                        animationSpec = tween(durationMillis = 200),
                        label = "lyricAlpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clickable { onSeek(line.timestampMs) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Word-by-word syllable lighting when active
                        if (isActive && line.hasSyllables) {
                            val annotated = buildAnnotatedString {
                                for (syl in line.syllables) {
                                    val isPast = currentPositionMs >= syl.timeMs + syl.durationMs
                                    val isCurrent = currentPositionMs in syl.timeMs until (syl.timeMs + syl.durationMs)
                                    val color = when {
                                        isPast || isCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    }

                                    withStyle(
                                        SpanStyle(
                                            color = color,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                            fontSize = if (isFullScreen) 24.sp else 20.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    ) {
                                        append(syl.text)
                                        if (syl.hasTrailingSpace && !syl.text.endsWith(" ")) {
                                            append(" ")
                                        }
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = if (isActive) {
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isFullScreen) 24.sp else 20.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = if (isFullScreen) 18.sp else 16.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                },
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Romanization Subline
                        if (!line.romanized.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = line.romanized,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.85f else 0.45f),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Translation Subline
                        if (!line.translation.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = line.translation,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 0.90f else 0.50f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
