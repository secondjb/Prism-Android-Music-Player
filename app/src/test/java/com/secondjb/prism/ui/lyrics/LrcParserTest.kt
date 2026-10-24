package com.secondjb.prism.ui.lyrics

import com.secondjb.prism.domain.model.DemoDataProvider
import com.secondjb.prism.ui.components.lyrics.LrcParser
import org.junit.Assert.assertEquals
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
}
