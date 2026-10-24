package com.secondjb.prism.ui.components.lyrics

import android.os.Build

data class LyricSyllable(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
    val hasTrailingSpace: Boolean = true
)

data class LyricLine(
    val timestampMs: Long,
    val durationMs: Long = 4000L,
    val text: String,
    val romanized: String? = null,
    val translation: String? = null,
    val syllables: List<LyricSyllable> = emptyList(),
    val hasSyllables: Boolean = syllables.isNotEmpty()
)

object LrcParser {
    private val LRC_LINE_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?](.*)""")
    private val INLINE_WORD_TAG_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?>([^<]*)""")
    private val INLINE_TRANSLATION_REGEX = Regex("""^(.+?)(?:\s*(?://|／／)\s*|\s+[/|／]\s+)(.+)$""")

    /**
     * Determines whether the raw text is Apple Music / TTML XML formatted.
     */
    fun isTtmlContent(text: String?): Boolean {
        if (text == null) return false
        val t = text.trim()
        return t.startsWith("<?xml") ||
               t.startsWith("<tt") ||
               (t.contains("<tt ") && t.contains("xmlns")) ||
               (t.contains("<p ") && t.contains("begin=") && (t.contains("<span") || t.contains("</p>")))
    }

    /**
     * Decodes common XML character entities.
     */
    fun decodeXmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                code.toChar().toString()
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull(16) ?: return@replace match.value
                code.toChar().toString()
            }
    }

    /**
     * Parses TTML time format: "0:12.369", "00:01:23.456", "12.34s", "1234ms".
     */
    fun parseTtmlTime(timeStr: String): Long {
        val s = timeStr.trim()
        if (s.isEmpty()) return 0L
        val colonParts = s.split(':')
        return when (colonParts.size) {
            3 -> {
                val h = colonParts[0].toDoubleOrNull() ?: 0.0
                val m = colonParts[1].toDoubleOrNull() ?: 0.0
                val sec = colonParts[2].toDoubleOrNull() ?: 0.0
                Math.round((h * 3600 + m * 60 + sec) * 1000.0)
            }
            2 -> {
                val m = colonParts[0].toDoubleOrNull() ?: 0.0
                val sec = colonParts[1].toDoubleOrNull() ?: 0.0
                Math.round((m * 60 + sec) * 1000.0)
            }
            else -> {
                when {
                    s.endsWith("ms", ignoreCase = true) -> {
                        s.removeSuffix("ms").toDoubleOrNull()?.let { Math.round(it) } ?: 0L
                    }
                    s.endsWith("s", ignoreCase = true) -> {
                        s.removeSuffix("s").toDoubleOrNull()?.let { Math.round(it * 1000.0) } ?: 0L
                    }
                    else -> {
                        s.toDoubleOrNull()?.let { Math.round(it * 1000.0) } ?: 0L
                    }
                }
            }
        }
    }

    private fun formatLrcTag(timeMs: Long): String {
        val totalSec = timeMs / 1000L
        val m = (totalSec / 60L).toString().padStart(2, '0')
        val sec = (totalSec % 60L).toString().padStart(2, '0')
        val ms = (timeMs % 1000L).toString().padStart(3, '0')
        return "[$m:$sec.$ms]"
    }

    private fun formatInlineTag(timeMs: Long): String {
        val totalSec = timeMs / 1000L
        val m = (totalSec / 60L).toString().padStart(2, '0')
        val sec = (totalSec % 60L).toString().padStart(2, '0')
        val ms = (timeMs % 1000L).toString().padStart(3, '0')
        return "<$m:$sec.$ms>"
    }

    /**
     * Converts TTML/XML lyrics into standard line/word-synced LRC format.
     */
    fun convertTtmlToLrc(ttmlStr: String): String {
        if (!isTtmlContent(ttmlStr)) return ttmlStr.trim()

        val cleanTtml = ttmlStr.replace("""\"""", "\"")
        val lines = mutableListOf<String>()

        val pRegex = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        val spanRegex = Regex("""<span\b([^>]*)>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
        val beginAttrRegex = Regex("""\bbegin=["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
        val transRegex = Regex("""<span\b[^>]*\bttm:role="translation"[^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
        val tagStripRegex = Regex("""<[^>]+>""")

        for (pMatch in pRegex.findAll(cleanTtml)) {
            val pAttrs = pMatch.groupValues[1]
            val pContent = pMatch.groupValues[2]

            val beginMatch = beginAttrRegex.find(pAttrs)
            val lineStartMs = if (beginMatch != null) parseTtmlTime(beginMatch.groupValues[1]) else 0L
            val lineTag = formatLrcTag(lineStartMs)

            var translationText = ""
            val transMatch = transRegex.find(pContent)
            if (transMatch != null) {
                translationText = decodeXmlEntities(transMatch.groupValues[1].replace(tagStripRegex, "").trim())
            }

            val spanMatches = spanRegex.findAll(pContent).toList()
            val wordBuilder = StringBuilder()
            var hasWordSpans = false

            for (spanMatch in spanMatches) {
                val spanAttrs = spanMatch.groupValues[1]
                if (spanAttrs.contains("ttm:role=\"translation\"", ignoreCase = true)) continue

                val rawSpanText = spanMatch.groupValues[2].replace(tagStripRegex, "")
                val spanText = decodeXmlEntities(rawSpanText).trim()
                if (spanText.isEmpty()) continue

                hasWordSpans = true
                val spanBeginMatch = beginAttrRegex.find(spanAttrs)
                val spanStartMs = if (spanBeginMatch != null) parseTtmlTime(spanBeginMatch.groupValues[1]) else lineStartMs
                val inlineTag = formatInlineTag(spanStartMs)

                wordBuilder.append(inlineTag).append(spanText).append(" ")
            }

            val transSuffix = if (translationText.isNotBlank()) " // $translationText" else ""

            if (hasWordSpans && wordBuilder.isNotBlank()) {
                lines.add("$lineTag ${wordBuilder.toString().trim()}$transSuffix")
            } else {
                val cleanText = decodeXmlEntities(pContent.replace(tagStripRegex, "").trim())
                if (cleanText.isNotBlank()) {
                    lines.add("$lineTag $cleanText$transSuffix")
                }
            }
        }

        if (lines.isEmpty()) {
            return decodeXmlEntities(ttmlStr.replace(tagStripRegex, " ").replace(Regex("""\s+"""), " ").trim())
        }

        return lines.joinToString("\n")
    }

    fun parse(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()

        // Handle TTML XML lyrics (Apple Music style)
        val processedContent = if (isTtmlContent(content)) {
            convertTtmlToLrc(content)
        } else {
            content
        }

        val rawLines = processedContent.lines()
        val tempLines = mutableListOf<Pair<Long, String>>()

        for (line in rawLines) {
            val trimmed = line.trim()
            val match = LRC_LINE_REGEX.matchEntire(trimmed)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val fractionMs = when (fractionStr.length) {
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100
                    else -> fractionStr.toLongOrNull() ?: 0L
                }
                val timestampMs = min * 60_000L + sec * 1_000L + fractionMs
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    tempLines.add(timestampMs to text)
                }
            }
        }

        // If not in LRC format, treat as plain text with estimated 4-second spacing
        if (tempLines.isEmpty()) {
            rawLines.filter { it.isNotBlank() }.forEachIndexed { index, text ->
                tempLines.add((index * 4000L) to text.trim())
            }
        }

        tempLines.sortBy { it.first }

        val result = mutableListOf<LyricLine>()
        var i = 0
        while (i < tempLines.size) {
            val (timeMs, rawText) = tempLines[i]
            val nextTimeMs = if (i < tempLines.lastIndex) tempLines[i + 1].first else timeMs + 4000L
            val lineDurationMs = (nextTimeMs - timeMs).coerceIn(1200L, 15000L)

            // Split out any inline translation (delimited by //, /, or |)
            val inlineSepMatch = INLINE_TRANSLATION_REGEX.find(rawText)
            var (primaryBody, inlineTranslation) = if (inlineSepMatch != null) {
                val prim = inlineSepMatch.groupValues[1].trim()
                val trans = inlineSepMatch.groupValues[2].replace(INLINE_WORD_TAG_REGEX, "").trim()
                prim to (if (trans.isNotBlank()) trans else null)
            } else {
                rawText to null
            }

            // Also check for adjacent lines within 150ms as a translation pair (e.g. CJK + English)
            if (inlineTranslation == null && i < tempLines.lastIndex) {
                val nextEntry = tempLines[i + 1]
                if (Math.abs(nextEntry.first - timeMs) <= 150L && nextEntry.second.trim() != rawText.trim()) {
                    var trans = nextEntry.second
                        .replace(INLINE_WORD_TAG_REGEX, "")
                        .replace(Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{2,3})?]"""), "")
                        .trim()
                    if (trans.startsWith("//") || trans.startsWith("／／")) {
                        trans = trans.removePrefix("//").removePrefix("／／").trim()
                    }
                    if (trans.isNotBlank()) {
                        inlineTranslation = trans
                        i++ // consume translation line
                    }
                }
            }

            // Check if line contains word-level inline tags like <00:12.34>Word
            val inlineMatches = INLINE_WORD_TAG_REGEX.findAll(primaryBody).toList()
            val syllables = if (inlineMatches.isNotEmpty()) {
                val sylList = mutableListOf<LyricSyllable>()
                for (j in inlineMatches.indices) {
                    val m = inlineMatches[j]
                    val sMin = m.groupValues[1].toLongOrNull() ?: 0L
                    val sSec = m.groupValues[2].toLongOrNull() ?: 0L
                    val sFracStr = m.groupValues[3]
                    val sFrac = when (sFracStr.length) {
                        2 -> (sFracStr.toLongOrNull() ?: 0L) * 10
                        1 -> (sFracStr.toLongOrNull() ?: 0L) * 100
                        else -> sFracStr.toLongOrNull() ?: 0L
                    }
                    val sTimeMs = sMin * 60_000L + sSec * 1_000L + sFrac
                    val sText = m.groupValues[4]
                    val nextSylTime = if (j < inlineMatches.lastIndex) {
                        val nm = inlineMatches[j + 1]
                        val nMin = nm.groupValues[1].toLongOrNull() ?: 0L
                        val nSec = nm.groupValues[2].toLongOrNull() ?: 0L
                        val nFracStr = nm.groupValues[3]
                        val nFrac = when (nFracStr.length) {
                            2 -> (nFracStr.toLongOrNull() ?: 0L) * 10
                            else -> nFracStr.toLongOrNull() ?: 0L
                        }
                        nMin * 60_000L + nSec * 1_000L + nFrac
                    } else {
                        timeMs + lineDurationMs
                    }
                    val sDur = (nextSylTime - sTimeMs).coerceAtLeast(100L)
                    sylList.add(LyricSyllable(sTimeMs, sDur, sText, j < inlineMatches.lastIndex))
                }
                sylList
            } else {
                // Musical vocal inference (adapted from Prism Windows lyricsParser)
                inferLineSyllables(primaryBody, timeMs, lineDurationMs)
            }

            val cleanText = if (inlineMatches.isNotEmpty()) {
                syllables.joinToString("") { it.text }.trim()
            } else {
                primaryBody
            }

            val romanized = romanizeText(cleanText)

            result.add(
                LyricLine(
                    timestampMs = timeMs,
                    durationMs = lineDurationMs,
                    text = cleanText,
                    romanized = romanized,
                    translation = inlineTranslation,
                    syllables = syllables,
                    hasSyllables = syllables.isNotEmpty()
                )
            )
            i++
        }

        return result
    }

    private fun inferLineSyllables(lineText: String, lineStartMs: Long, lineDurMs: Long): List<LyricSyllable> {
        val words = lineText.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val singSpanMs = minOf(
            maxOf(600L, lineDurMs - 200L),
            maxOf(800L, (lineDurMs * 0.88f).toLong())
        )

        val weights = words.map { maxOf(2, it.length) }
        val totalWeight = weights.sum().coerceAtLeast(1)

        val syllables = mutableListOf<LyricSyllable>()
        var elapsed = 0L

        for (i in words.indices) {
            val word = words[i]
            val weight = weights[i]
            val isLast = i == words.lastIndex
            val wordDurMs = if (isLast) {
                maxOf(150L, singSpanMs - elapsed)
            } else {
                maxOf(150L, (weight.toFloat() / totalWeight.toFloat() * singSpanMs).toLong())
            }

            syllables.add(
                LyricSyllable(
                    timeMs = lineStartMs + elapsed,
                    durationMs = wordDurMs,
                    text = word,
                    hasTrailingSpace = !isLast
                )
            )

            elapsed += wordDurMs
        }

        return syllables
    }

    /**
     * Unified CJK Romanizer supporting Japanese (Kanji/Kana -> Romaji), Chinese (Hanzi -> Pinyin),
     * and Korean (Hangul -> Revised Romanization), matching the Prism Windows lyrics romanizer.
     */
    fun romanizeText(text: String): String? {
        if (text.isBlank()) return null

        val hasCjk = text.any { c ->
            val code = c.code
            // Hangul, Hanzi / Kanji (CJK Unified Ideographs), Hiragana, Katakana
            (code in 0xAC00..0xD7A3) || (code in 0x4E00..0x9FAF) || (code in 0x3040..0x309F) || (code in 0x30A0..0x30FF)
        }
        if (!hasCjk) return null

        // 1. Android ICU Transliterator (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Try Any-Latin or Han-Latin for Chinese and Japanese
                val transliterator = android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
                val result = transliterator.transliterate(text).trim()
                if (result.isNotBlank() && result != text.trim()) {
                    return result
                }
            } catch (_: Throwable) {}
        }

        // 2. Comprehensive fallback phonetics for Kana & Hangul
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val code = c.code

            // Korean Hangul Syllables (0xAC00..0xD7A3)
            if (code in 0xAC00..0xD7A3) {
                val syIdx = code - 0xAC00
                val initialIdx = (syIdx / 28) / 21
                val vowelIdx = (syIdx / 28) % 21
                val finalIdx = syIdx % 28

                val initials = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
                val vowels = arrayOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
                val finals = arrayOf("", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h")

                sb.append(initials.getOrElse(initialIdx) { "" })
                sb.append(vowels.getOrElse(vowelIdx) { "" })
                sb.append(finals.getOrElse(finalIdx) { "" })
                i++
                continue
            }

            // Japanese compound kana (2 characters)
            if (i < text.length - 1) {
                val compound = text.substring(i, i + 2)
                val compRomaji = KANA_COMPOUND_ROMAJI_MAP[compound]
                if (compRomaji != null) {
                    sb.append(compRomaji)
                    i += 2
                    continue
                }
            }

            // Japanese Hiragana & Katakana single characters
            val romaji = KANA_SINGLE_ROMAJI_MAP[c.toString()]
            if (romaji != null) {
                sb.append(romaji)
                i++
                continue
            }

            sb.append(c)
            i++
        }

        val out = sb.toString().trim()
        return if (out.isNotBlank() && out != text.trim()) out else null
    }

    private val KANA_COMPOUND_ROMAJI_MAP = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo"
    )

    private val KANA_SINGLE_ROMAJI_MAP = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "wo", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "へ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "wo", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po"
    )
}
