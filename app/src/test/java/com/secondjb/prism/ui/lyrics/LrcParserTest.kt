package com.secondjb.prism.ui.lyrics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.secondjb.prism.domain.model.DemoDataProvider
import com.secondjb.prism.ui.components.lyrics.LrcParser
import com.secondjb.prism.ui.components.lyrics.LyricLine
import com.secondjb.prism.ui.components.lyrics.WordSyncedLyricsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrcLines() {
        val lrc = """
            [00:04.50]Dancing in the shadows of the neon light
            [00:09.20]Listening to waveforms cutting through the night
            [01:14.00]Crystal clear frequencies, lossless in the air
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.size)

        assertEquals(4500L, parsed[0].timestampMs)
        assertEquals("Dancing in the shadows of the neon light", parsed[0].text)

        assertEquals(9200L, parsed[1].timestampMs)
        assertEquals("Listening to waveforms cutting through the night", parsed[1].text)

        assertEquals(74000L, parsed[2].timestampMs)
        assertEquals("Crystal clear frequencies, lossless in the air", parsed[2].text)
    }

    @Test
    fun testParseSampleLrcFromDemoData() {
        val parsed = LrcParser.parse(DemoDataProvider.SAMPLE_LRC_1)
        assertTrue(parsed.isNotEmpty())
        assertEquals(11, parsed.size)
        // Verify timestamps are in ascending order
        for (i in 0 until parsed.size - 1) {
            assertTrue(parsed[i].timestampMs <= parsed[i + 1].timestampMs)
        }
    }

    @Test
    fun testPlainTextFallback() {
        val plainText = """
            Line one without timestamps
            Line two without timestamps
        """.trimIndent()

        val parsed = LrcParser.parse(plainText)
        assertEquals(2, parsed.size)
        assertEquals("Line one without timestamps", parsed[0].text)
        assertEquals("Line two without timestamps", parsed[1].text)
    }

    @Test
    fun testParseInlineTranslationWithDoubleSlash() {
        val lrc = """
            [00:04.50]Dancing in the shadows // Bailando en las sombras
            [00:09.20]君が好きだと叫びたい // I want to shout I love you
            [00:15.00]<00:15.00>Word <00:15.50>tag // Tagged translation
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.size)

        assertEquals("Dancing in the shadows", parsed[0].text)
        assertEquals("Bailando en las sombras", parsed[0].translation)

        assertEquals("君が好きだと叫びたい", parsed[1].text)
        assertEquals("I want to shout I love you", parsed[1].translation)

        assertEquals("Word tag", parsed[2].text)
        assertEquals("Tagged translation", parsed[2].translation)
    }

    @Test
    fun testAdjacentLinesTranslationPair() {
        val lrc = """
            [00:04.50]Original Lyric
            [00:04.50]// Translated Lyric
            [00:10.00]Next Lyric
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(2, parsed.size)
        assertEquals("Original Lyric", parsed[0].text)
        assertEquals("Translated Lyric", parsed[0].translation)
        assertEquals("Next Lyric", parsed[1].text)
    }

    @Test
    fun testParseTtmlXmlLyrics() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                <body>
                    <div>
                        <p begin="0:12.369" end="0:15.921">
                            <span begin="0:12.369">Dear</span> <span begin="0:12.541">my</span> <span begin="0:13.091">crazy</span> <span begin="0:14.388">soulmate</span>
                        </p>
                        <p begin="0:16.238" end="0:18.962">
                            <span begin="0:16.238">결국에</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val parsed = LrcParser.parse(ttml)
        assertEquals(2, parsed.size)
        assertEquals(12369L, parsed[0].timestampMs)
        assertEquals("Dear my crazy soulmate", parsed[0].text)
        assertTrue(parsed[0].hasSyllables)
        assertEquals(4, parsed[0].syllables.size)

        assertEquals(16238L, parsed[1].timestampMs)
        assertEquals("결국에", parsed[1].text)
        assertEquals("gyeolguke", parsed[1].syllables[0].romanizedText)
        assertEquals(parsed[1].romanized, parsed[1].syllables[0].romanizedText)
    }

    @Test
    fun testWordSyncedSyllableRomanizationAndContiguousGrouping() {
        // Contiguous Japanese kana without spaces should NOT force spaces between syllables,
        // while space-delimited words keep hasTrailingSpace = true.
        val lrc = "[00:10.00]<00:10.00>さ<00:10.30>く<00:10.60>ら <00:11.00>ま<00:11.30>う"
        val parsed = LrcParser.parse(lrc)
        assertEquals(1, parsed.size)
        val line = parsed[0]
        assertTrue(line.hasExplicitSyllables)
        assertEquals(5, line.syllables.size)

        assertEquals("sa", line.syllables[0].romanizedText)
        assertFalse(line.syllables[0].hasTrailingSpace)
        assertEquals("ku", line.syllables[1].romanizedText)
        assertFalse(line.syllables[1].hasTrailingSpace)
        assertEquals("ra", line.syllables[2].romanizedText)
        assertTrue(line.syllables[2].hasTrailingSpace)
        assertEquals("ma", line.syllables[3].romanizedText)
        assertFalse(line.syllables[3].hasTrailingSpace)
        assertEquals("u", line.syllables[4].romanizedText)
        assertFalse(line.syllables[4].hasTrailingSpace)

        val annotated = WordSyncedLyricsHelper.buildRomanizedSyncedAnnotatedString(
            line = line,
            currentPosMs = 10400L,
            isActive = true,
            activeColor = Color.White,
            upcomingColor = Color.Gray,
            inactiveColor = Color.DarkGray,
            fontSize = 20.sp
        )
        assertEquals("sakura mau", annotated.text)
        assertEquals(5, annotated.spanStyles.size)
        // First two syllables ("sa", "ku") are past/active -> White; upcoming ("ra") -> Gray
        assertEquals(Color.White, annotated.spanStyles[0].item.color)
        assertEquals(Color.White, annotated.spanStyles[1].item.color)
        assertEquals(Color.Gray, annotated.spanStyles[2].item.color)
    }

    @Test
    fun testWordTimedRomanizedFallbackWhenSyllablesLackRomanizedText() {
        val line = LyricLine(
            timestampMs = 10_000L,
            durationMs = 3_000L,
            text = "한글 가사 테스트",
            romanized = "hangeul gasa teseuteu",
            hasExplicitSyllables = true,
            hasSyllables = true
        )
        val annotated = WordSyncedLyricsHelper.buildRomanizedSyncedAnnotatedString(
            line = line,
            currentPosMs = 11_500L,
            isActive = true,
            activeColor = Color.White,
            upcomingColor = Color.Gray,
            inactiveColor = Color.DarkGray,
            fontSize = 18.sp
        )
        assertEquals("hangeul gasa teseuteu", annotated.text)
        assertEquals(3, annotated.spanStyles.size)
        assertEquals(Color.White, annotated.spanStyles[0].item.color)
        assertEquals(Color.White, annotated.spanStyles[1].item.color)
        assertEquals(Color.Gray, annotated.spanStyles[2].item.color)
    }
}
