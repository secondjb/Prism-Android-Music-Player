package com.secondjb.prism.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.components.FluidArtworkBackground
import com.secondjb.prism.ui.components.WavySeekBar
import com.secondjb.prism.ui.components.lyrics.LyricLine
import com.secondjb.prism.ui.components.lyrics.LrcParser
import com.secondjb.prism.ui.theme.PillShape
import kotlinx.coroutines.delay

/**
 * Dedicated Full-Screen Lyrics Page with word-by-word synced highlighting,
 * romanization & translation sublines, LastWave wavy seekbar, and auto-fading playback controls.
 */
@Composable
fun FullScreenLyricsPage(
    track: Track,
    playbackState: PlaybackState,
    lyricsText: String?,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    var showControls by remember { mutableStateOf(true) }
    var isRomanizationVisible by remember { mutableStateOf(true) }
    var isTranslationVisible by remember { mutableStateOf(true) }

    // Auto-fade controls after 3.5s idle
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500L)
            showControls = false
        }
    }

    val parsedLines = remember(lyricsText) {
        if (!lyricsText.isNullOrBlank()) LrcParser.parse(lyricsText) else emptyList()
    }

    val activeIndex by remember(playbackState.positionMs, parsedLines) {
        derivedStateOf {
            val idx = parsedLines.indexOfLast { it.timestampMs <= playbackState.positionMs }
            if (idx == -1 && parsedLines.isNotEmpty()) 0 else idx
        }
    }

    val listState = rememberLazyListState()

    // Smooth auto-scroll keeping active lyric line near center
    LaunchedEffect(activeIndex) {
        if (activeIndex in parsedLines.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        // Fluid Ambient Artwork Backdrop
        FluidArtworkBackground(artworkUri = track.artworkUri)

        // Dark Frosted Glass Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        // Center Lyrics Stream
        if (parsedLines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No synchronized lyrics available",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 180.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                itemsIndexed(parsedLines, key = { index, line -> "$index-${line.timestampMs}" }) { index, line ->
                    val isActive = index == activeIndex
                    val isPast = index < activeIndex

                    val targetAlpha = when {
                        isActive -> 1.0f
                        isPast -> 0.45f
                        else -> 0.65f
                    }

                    LyricLineRow(
                        line = line,
                        isActive = isActive,
                        currentPosMs = playbackState.positionMs,
                        alpha = targetAlpha,
                        showRomanization = isRomanizationVisible,
                        showTranslation = isTranslationVisible,
                        onClick = {
                            onSeek(line.timestampMs)
                            showControls = true
                        }
                    )
                }
            }
        }

        // Top Floating Header (Fade Away)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Fullscreen Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        // Romanization Toggle
                        IconButton(onClick = { isRomanizationVisible = !isRomanizationVisible }) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Toggle Romanization",
                                tint = if (isRomanizationVisible) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Translation Toggle
                        IconButton(onClick = { isTranslationVisible = !isTranslationVisible }) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Toggle Translation",
                                tint = if (isTranslationVisible) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Player Controls & Seekbar (Fade Away)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Wavy Seekbar
                    WavySeekBar(
                        positionMs = playbackState.positionMs,
                        durationMs = playbackState.durationMs,
                        isPlaying = playbackState.isPlaying,
                        onSeek = onSeek,
                        isTranslucent = true,
                        trackKey = track.id,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.secondary,
                        tertiaryColor = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(Modifier.height(10.dp))

                    // Playback Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    currentPosMs: Long,
    alpha: Float,
    showRomanization: Boolean,
    showTranslation: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Main Text with Word-by-Word Syllable Highlighting
        if (isActive && line.hasSyllables) {
            val annotated = buildAnnotatedString {
                for (syl in line.syllables) {
                    val isPastSyl = currentPosMs >= syl.timeMs + syl.durationMs
                    val isCurrentSyl = currentPosMs in syl.timeMs until (syl.timeMs + syl.durationMs)

                    val sylColor = when {
                        isPastSyl || isCurrentSyl -> Color.White
                        else -> Color.White.copy(alpha = 0.40f)
                    }

                    withStyle(
                        style = SpanStyle(
                            color = sylColor,
                            fontWeight = if (isCurrentSyl) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    ) {
                        append(syl.text)
                        if (syl.hasTrailingSpace) append(" ")
                    }
                }
            }

            Text(
                text = annotated,
                lineHeight = 36.sp
            )
        } else {
            Text(
                text = line.text,
                fontSize = if (isActive) 28.sp else 22.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color.White.copy(alpha = alpha),
                lineHeight = if (isActive) 36.sp else 30.sp
            )
        }

        // Romanization Subline (Phonetic pronunciation)
        if (showRomanization && !line.romanized.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = line.romanized,
                fontSize = if (isActive) 15.sp else 13.sp,
                fontWeight = FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f),
                lineHeight = 18.sp
            )
        }

        // Translation Subline
        if (showTranslation && !line.translation.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.translation,
                fontSize = if (isActive) 15.sp else 13.sp,
                fontWeight = FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.35f),
                lineHeight = 18.sp
            )
        }
    }
}
