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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.components.FluidArtworkBackground
import com.secondjb.prism.ui.components.WavySeekBar
import com.secondjb.prism.ui.components.lyrics.LyricLine
import com.secondjb.prism.ui.components.lyrics.LrcParser
import kotlinx.coroutines.delay

/**
 * Dedicated Full-Screen Lyrics Page matching PixelPlayer aesthetic and Prism Windows functionality:
 * - Fluid animated artwork backdrop with deep frosted blur scrim.
 * - Word-by-word synced highlighting and romanization / translation sublines.
 * - Auto-fades controls after idle, reappears instantly on tap or scroll.
 * - Status bars and navigation bars padding for unobstructed touch targets.
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
    var fontScale by remember { mutableFloatStateOf(1.0f) }

    val listState = rememberLazyListState()

    // When the user starts scrolling, show all buttons immediately!
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            showControls = true
        }
    }

    // Auto-fade controls after 4 seconds idle
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000L)
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

    // Smooth auto-scroll keeping active lyric line near center when user isn't scrolling
    LaunchedEffect(activeIndex, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && activeIndex in parsedLines.indices) {
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
                .background(Color.Black.copy(alpha = 0.58f))
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
                contentPadding = PaddingValues(top = 120.dp, bottom = 200.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(parsedLines, key = { index, line -> "$index-${line.timestampMs}" }) { index, line ->
                    val isActive = index == activeIndex
                    val isPast = index < activeIndex

                    val targetAlpha = when {
                        isActive -> 1.0f
                        isPast -> 0.40f
                        else -> 0.60f
                    }

                    LyricLineRow(
                        line = line,
                        isActive = isActive,
                        currentPosMs = playbackState.positionMs,
                        alpha = targetAlpha,
                        fontScale = fontScale,
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

        // Top Floating Header (With subtle gradient, safely below status bar notch)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / Exit Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Fullscreen Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Track Title & Artist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
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

                    // Utility Action Buttons Row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Font Size Cycle Toggle
                        IconButton(
                            onClick = {
                                fontScale = when (fontScale) {
                                    1.0f -> 1.15f
                                    1.15f -> 1.30f
                                    else -> 1.0f
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (fontScale > 1.0f) 0.25f else 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextIncrease,
                                contentDescription = "Cycle Font Size",
                                tint = if (fontScale > 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Romanization Toggle
                        IconButton(
                            onClick = { isRomanizationVisible = !isRomanizationVisible },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isRomanizationVisible) 0.25f else 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Toggle Romanization",
                                tint = if (isRomanizationVisible) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Translation Toggle
                        IconButton(
                            onClick = { isTranslationVisible = !isTranslationVisible },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isTranslationVisible) 0.25f else 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Toggle Translation",
                                tint = if (isTranslationVisible) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Player Controls & Wavy Seekbar (With gradient scrim, safely above navigation bars)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Wavy Animated Seekbar
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

                    Spacer(Modifier.height(12.dp))

                    // Playback Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
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
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
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
    fontScale: Float,
    showRomanization: Boolean,
    showTranslation: Boolean,
    onClick: () -> Unit
) {
    val activeBaseSize = (28f * fontScale).sp
    val inactiveBaseSize = (22f * fontScale).sp
    val activeLineHeight = (36f * fontScale).sp
    val inactiveLineHeight = (30f * fontScale).sp

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
                            fontSize = activeBaseSize
                        )
                    ) {
                        append(syl.text)
                        if (syl.hasTrailingSpace) append(" ")
                    }
                }
            }

            Text(
                text = annotated,
                lineHeight = activeLineHeight
            )
        } else {
            Text(
                text = line.text,
                fontSize = if (isActive) activeBaseSize else inactiveBaseSize,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color.White.copy(alpha = alpha),
                lineHeight = if (isActive) activeLineHeight else inactiveLineHeight
            )
        }

        // Romanization Subline (Phonetic pronunciation)
        if (showRomanization && !line.romanized.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = line.romanized,
                fontSize = ((if (isActive) 15f else 13f) * fontScale).sp,
                fontWeight = FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f),
                lineHeight = (18f * fontScale).sp
            )
        }

        // Translation Subline
        if (showTranslation && !line.translation.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.translation,
                fontSize = ((if (isActive) 15f else 13f) * fontScale).sp,
                fontWeight = FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.35f),
                lineHeight = (18f * fontScale).sp
            )
        }
    }
}
