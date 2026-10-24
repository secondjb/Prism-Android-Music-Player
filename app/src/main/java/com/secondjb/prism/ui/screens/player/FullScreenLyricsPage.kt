package com.secondjb.prism.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    var isUserScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        isUserScrolled = false
    }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    fun resetControlsTimer() {
        lastInteractionTime = System.currentTimeMillis()
        showControls = true
    }

    // Detect manual user touch drag gestures (distinguishing user interaction from programmatic scroll)
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                isUserScrolled = true
                resetControlsTimer()
            }
        }
    }

    // When the user is scrolling manually, reveal playback controls immediately and keep them alive!
    LaunchedEffect(listState.isScrollInProgress, isUserScrolled) {
        if (listState.isScrollInProgress && isUserScrolled) {
            resetControlsTimer()
        }
    }

    // Auto-fade controls only after 7.5 seconds of complete inactivity (no scrolling, tapping, or scrubbing)
    LaunchedEffect(showControls, lastInteractionTime, listState.isScrollInProgress) {
        if (showControls && !listState.isScrollInProgress) {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                val remaining = 7500L - elapsed
                if (remaining <= 0) {
                    showControls = false
                    break
                }
                delay(remaining)
            }
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

    // Smooth auto-scroll keeping active lyric line near center when user hasn't manually scrolled away
    LaunchedEffect(activeIndex, isUserScrolled) {
        if (!isUserScrolled && activeIndex in parsedLines.indices) {
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
                        if (!showControls) resetControlsTimer() else showControls = false
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
                            isUserScrolled = false
                            resetControlsTimer()
                        }
                    )
                }
            }
        }

        // Top Floating Pill Header (Opaque pill matching FloatingNowPlayingBar aesthetic & colors)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .zIndex(10f)
        ) {
            ElevatedCard(
                shape = CircleShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / Exit Button (naked icon, no background circle)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Fullscreen Lyrics",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Track Title & Artist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Utility Action Buttons Row (Naked icons without circular background; color indicates ON/OFF)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Font Size Cycle Toggle
                        IconButton(
                            onClick = {
                                resetControlsTimer()
                                fontScale = when (fontScale) {
                                    1.0f -> 1.15f
                                    1.15f -> 1.30f
                                    else -> 1.0f
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextIncrease,
                                contentDescription = "Cycle Font Size",
                                tint = if (fontScale > 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Romanization Toggle
                        IconButton(
                            onClick = {
                                resetControlsTimer()
                                isRomanizationVisible = !isRomanizationVisible
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Toggle Romanization",
                                tint = if (isRomanizationVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Translation Toggle
                        IconButton(
                            onClick = {
                                resetControlsTimer()
                                isTranslationVisible = !isTranslationVisible
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Toggle Translation",
                                tint = if (isTranslationVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating "Sync to lyrics" pill button when user has scrolled away from the active line
        AnimatedVisibility(
            visible = isUserScrolled && parsedLines.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) 180.dp else 40.dp)
                .navigationBarsPadding()
                .zIndex(15f)
        ) {
            androidx.compose.material3.Surface(
                onClick = {
                    isUserScrolled = false
                    resetControlsTimer()
                    coroutineScope.launch {
                        if (activeIndex in parsedLines.indices) {
                            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(targetScroll)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sync to lyrics",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                        onSeek = { resetControlsTimer(); onSeek(it) },
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
                            onClick = { resetControlsTimer(); onSkipPrevious() },
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
                            onClick = { resetControlsTimer(); onTogglePlayPause() },
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
                            onClick = { resetControlsTimer(); onSkipNext() },
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
                            fontSize = activeBaseSize,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    ) {
                        append(syl.text)
                        if (syl.hasTrailingSpace && !syl.text.endsWith(" ")) append(" ")
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
