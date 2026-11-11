package com.secondjb.prism.ui.components.lyrics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

/**
 * Shared helpers for word-by-word / syllable-by-syllable lyric synchronization,
 * mirroring Prism Windows' `LyricLineRow.tsx` behavior for original and romanized lyrics.
 */
object WordSyncedLyricsHelper {

    fun hasSyllableRomanization(line: LyricLine): Boolean =
        line.syllables.any { !it.romanizedText.isNullOrBlank() }

    /**
     * Builds an [AnnotatedString] for a line with explicit syllables (either original text or
     * per-syllable romanized text). When [isActive] is false, renders uniform [inactiveColor]
     * with the exact same text layout so lines do not jump when becoming active.
     */
    fun buildSyllableSyncedAnnotatedString(
        syllables: List<LyricSyllable>,
        currentPosMs: Long,
        isActive: Boolean,
        useRomanized: Boolean,
        activeColor: Color,
        upcomingColor: Color,
        inactiveColor: Color,
        fontSize: TextUnit,
        fontFamily: FontFamily = FontFamily.SansSerif,
        fontWeightNormal: FontWeight = FontWeight.Bold,
        fontWeightCurrent: FontWeight = FontWeight.ExtraBold
    ): AnnotatedString = buildAnnotatedString {
        for (i in syllables.indices) {
            val syl = syllables[i]
            val displayText = if (useRomanized) (syl.romanizedText ?: syl.text) else syl.text
            val isLast = i == syllables.lastIndex

            val sylColor: Color
            val sylWeight: FontWeight
            if (isActive) {
                val isPastSyl = currentPosMs >= syl.timeMs + syl.durationMs
                val isCurrentSyl = currentPosMs in syl.timeMs until (syl.timeMs + syl.durationMs)
                sylColor = if (isPastSyl || isCurrentSyl) activeColor else upcomingColor
                sylWeight = if (isCurrentSyl) fontWeightCurrent else fontWeightNormal
            } else {
                sylColor = inactiveColor
                sylWeight = fontWeightNormal
            }

            withStyle(
                style = SpanStyle(
                    color = sylColor,
                    fontWeight = sylWeight,
                    fontSize = fontSize,
                    fontFamily = fontFamily
                )
            ) {
                append(displayText)
                if (!isLast && syl.hasTrailingSpace && !displayText.endsWith(" ")) {
                    append(" ")
                }
            }
        }
    }

    /**
     * Fallback word-timed [AnnotatedString] matching Prism Windows' `renderSyllableTransWords`
     * when a word-synced line has `line.romanized` without per-syllable `romanizedText`.
     */
    fun buildWordTimedAnnotatedString(
        text: String,
        lineStartMs: Long,
        lineDurationMs: Long,
        currentPosMs: Long,
        isActive: Boolean,
        activeColor: Color,
        upcomingColor: Color,
        inactiveColor: Color,
        fontSize: TextUnit,
        fontFamily: FontFamily = FontFamily.SansSerif,
        fontWeightNormal: FontWeight = FontWeight.Bold,
        fontWeightCurrent: FontWeight = FontWeight.ExtraBold
    ): AnnotatedString {
        val words = text.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return AnnotatedString(text)

        val wordDurMs = lineDurationMs.coerceAtLeast(1L).toDouble() / words.size
        return buildAnnotatedString {
            for (i in words.indices) {
                val wordStart = lineStartMs + (i * wordDurMs).toLong()
                val wordEnd = lineStartMs + ((i + 1) * wordDurMs).toLong()

                val wordColor: Color
                val wordWeight: FontWeight
                if (isActive) {
                    val isPastWord = currentPosMs >= wordEnd
                    val isCurrentWord = currentPosMs in wordStart until wordEnd
                    wordColor = if (isPastWord || isCurrentWord) activeColor else upcomingColor
                    wordWeight = if (isCurrentWord) fontWeightCurrent else fontWeightNormal
                } else {
                    wordColor = inactiveColor
                    wordWeight = fontWeightNormal
                }

                withStyle(
                    style = SpanStyle(
                        color = wordColor,
                        fontWeight = wordWeight,
                        fontSize = fontSize,
                        fontFamily = fontFamily
                    )
                ) {
                    append(words[i])
                    if (i < words.lastIndex) append(" ")
                }
            }
        }
    }

    /**
     * Convenience wrapper that chooses per-syllable romanized timing when available,
     * falling back to evenly distributed word timing over [line.romanized].
     */
    fun buildRomanizedSyncedAnnotatedString(
        line: LyricLine,
        currentPosMs: Long,
        isActive: Boolean,
        activeColor: Color,
        upcomingColor: Color,
        inactiveColor: Color,
        fontSize: TextUnit,
        fontFamily: FontFamily = FontFamily.SansSerif,
        fontWeightNormal: FontWeight = FontWeight.Bold,
        fontWeightCurrent: FontWeight = FontWeight.ExtraBold
    ): AnnotatedString {
        return if (hasSyllableRomanization(line)) {
            buildSyllableSyncedAnnotatedString(
                syllables = line.syllables,
                currentPosMs = currentPosMs,
                isActive = isActive,
                useRomanized = true,
                activeColor = activeColor,
                upcomingColor = upcomingColor,
                inactiveColor = inactiveColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeightNormal = fontWeightNormal,
                fontWeightCurrent = fontWeightCurrent
            )
        } else {
            buildWordTimedAnnotatedString(
                text = line.romanized.orEmpty(),
                lineStartMs = line.timestampMs,
                lineDurationMs = line.durationMs,
                currentPosMs = currentPosMs,
                isActive = isActive,
                activeColor = activeColor,
                upcomingColor = upcomingColor,
                inactiveColor = inactiveColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeightNormal = fontWeightNormal,
                fontWeightCurrent = fontWeightCurrent
            )
        }
    }
}
