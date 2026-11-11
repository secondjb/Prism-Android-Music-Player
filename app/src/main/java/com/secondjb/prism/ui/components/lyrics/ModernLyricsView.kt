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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onToggleFullScreen: () -> Unit = {},
    fontScale: Float = 1.0f,
    romanizationMode: LyricDisplayMode = LyricDisplayMode.OFF,
    translationMode: LyricDisplayMode = LyricDisplayMode.OFF
) {
    if (lyricsText.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InstrumentalDotsIndicator(
                    startMs = 0L,
                    endMs = 10000L,
                    currentPositionMs = currentPositionMs,
                    isPlaying = true,
                    isActive = true,
                    isContinuous = true
                )
                Text(
                    text = "Could not find lyrics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        return
    }

    val parsedLines = remember(lyricsText) { LrcParser.parse(lyricsText) }
    val interludeList = remember(parsedLines) { computeLyricInterludes(parsedLines) }
    val activeInterlude by remember(currentPositionMs, interludeList) {
        derivedStateOf {
            interludeList.find { currentPositionMs in it.startMs until it.endMs }
        }
    }
    val activeIndex by remember(currentPositionMs, parsedLines) {
        derivedStateOf {
            val idx = parsedLines.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx == -1 && parsedLines.isNotEmpty()) 0 else idx
        }
    }

    val listState = rememberLazyListState()

    // Keep active lyric line or interlude centered in the preview card viewport
    LaunchedEffect(activeIndex, activeInterlude?.key) {
        val currentInterlude = activeInterlude
        if (currentInterlude != null) {
            val targetScroll = (currentInterlude.insertIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(index = targetScroll, scrollOffset = -80)
        } else if (activeIndex in parsedLines.indices) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(index = targetScroll, scrollOffset = -80)
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
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
                        color = Color.White,
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
                    val interludeBefore = interludeList.find { it.insertIndex == index }
                    if (interludeBefore != null) {
                        val isInterludeActive = currentPositionMs in interludeBefore.startMs until interludeBefore.endMs
                        if (isInterludeActive || (index == 0 && currentPositionMs < interludeBefore.endMs)) {
                            InstrumentalDotsIndicator(
                                startMs = interludeBefore.startMs,
                                endMs = interludeBefore.endMs,
                                currentPositionMs = currentPositionMs,
                                isPlaying = true,
                                isActive = true,
                                onSeek = { onSeek(interludeBefore.startMs) }
                            )
                        }
                    }

                    val isActive = index == activeIndex && activeInterlude == null
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(durationMillis = 200),
                        label = "lyricTextColor"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.35f,
                        animationSpec = tween(durationMillis = 200),
                        label = "lyricAlpha"
                    )

                    val hasTranslation = !line.translation.isNullOrBlank()
                    val hasRomanized = !line.romanized.isNullOrBlank()

                    val isTranslationReplacing = translationMode == LyricDisplayMode.REPLACE && hasTranslation
                    val isRomanizationReplacing = !isTranslationReplacing && romanizationMode == LyricDisplayMode.REPLACE && hasRomanized

                    val displayText = when {
                        isTranslationReplacing -> line.translation!!
                        isRomanizationReplacing -> line.romanized!!
                        else -> line.text
                    }

                    val activeFontSize = (if (isFullScreen) 24f else 20f) * fontScale
                    val inactiveFontSize = (if (isFullScreen) 18f else 16f) * fontScale
                    val sublineFontSize = (if (isActive) 14f else 12f) * fontScale

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clickable { onSeek(line.timestampMs) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Word-by-word syllable lighting when line has explicit syllables (supports original & romanized REPLACE mode)
                        val hasWordSync = line.hasExplicitSyllables && line.hasSyllables
                        if (hasWordSync && !isTranslationReplacing && (isActive || isRomanizationReplacing)) {
                            val annotated = if (isRomanizationReplacing) {
                                WordSyncedLyricsHelper.buildRomanizedSyncedAnnotatedString(
                                    line = line,
                                    currentPosMs = currentPositionMs,
                                    isActive = isActive,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    upcomingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    inactiveColor = textColor,
                                    fontSize = (if (isActive) activeFontSize else inactiveFontSize).sp,
                                    fontWeightNormal = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontWeightCurrent = FontWeight.ExtraBold
                                )
                            } else {
                                WordSyncedLyricsHelper.buildSyllableSyncedAnnotatedString(
                                    syllables = line.syllables,
                                    currentPosMs = currentPositionMs,
                                    isActive = true,
                                    useRomanized = false,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    upcomingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    inactiveColor = textColor,
                                    fontSize = activeFontSize.sp,
                                    fontWeightNormal = FontWeight.Bold,
                                    fontWeightCurrent = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = annotated,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = displayText,
                                style = if (isActive) {
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = activeFontSize.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = inactiveFontSize.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                },
                                color = textColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Romanization Subline (shown when in BELOW mode or when REPLACE overridden by translation)
                        val showRomanizationBelow = (romanizationMode == LyricDisplayMode.BELOW || (romanizationMode == LyricDisplayMode.REPLACE && isTranslationReplacing)) && hasRomanized
                        if (showRomanizationBelow) {
                            Spacer(Modifier.height(3.dp))
                            if (hasWordSync) {
                                val annotatedSubRom = WordSyncedLyricsHelper.buildRomanizedSyncedAnnotatedString(
                                    line = line,
                                    currentPosMs = currentPositionMs,
                                    isActive = isActive,
                                    activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                                    upcomingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                                    inactiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    fontSize = sublineFontSize.sp,
                                    fontWeightNormal = FontWeight.Normal,
                                    fontWeightCurrent = FontWeight.Bold
                                )
                                Text(
                                    text = annotatedSubRom,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = line.romanized!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = sublineFontSize.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.85f else 0.45f),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Translation Subline (shown when in BELOW mode)
                        val showTranslationBelow = translationMode == LyricDisplayMode.BELOW && hasTranslation
                        if (showTranslationBelow) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = line.translation!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = sublineFontSize.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 0.90f else 0.50f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
