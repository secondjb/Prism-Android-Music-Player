package com.secondjb.prism.ui.components.lyrics

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

    fun parse(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()

        val rawLines = content.lines()
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
        for (i in tempLines.indices) {
            val (timeMs, rawText) = tempLines[i]
            val nextTimeMs = if (i < tempLines.lastIndex) tempLines[i + 1].first else timeMs + 4000L
            val lineDurationMs = (nextTimeMs - timeMs).coerceIn(1200L, 15000L)

            // Check if line contains word-level inline tags like <00:12.34>Word
            val inlineMatches = INLINE_WORD_TAG_REGEX.findAll(rawText).toList()
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
                inferLineSyllables(rawText, timeMs, lineDurationMs)
            }

            val cleanText = if (inlineMatches.isNotEmpty()) {
                syllables.joinToString("") { it.text }.trim()
            } else {
                rawText
            }

            val romanized = romanizeText(cleanText)

            result.add(
                LyricLine(
                    timestampMs = timeMs,
                    durationMs = lineDurationMs,
                    text = cleanText,
                    romanized = romanized,
                    syllables = syllables,
                    hasSyllables = syllables.isNotEmpty()
                )
            )
        }

        return result
    }

    /**
     * Intelligently infers word-by-word timing for standard line-synced lyrics based on
     * word length weighting and vocal pacing (Prism Windows algorithm).
     */
    private fun inferLineSyllables(lineText: String, lineStartMs: Long, lineDurMs: Long): List<LyricSyllable> {
        val words = lineText.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        // Singing usually spans about 88% of line duration, leaving a natural breath pause
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
     * Romanizes Japanese Hiragana/Katakana and Korean Hangul to phonetic Latin scripts.
     */
    private fun romanizeText(text: String): String? {
        var hasCjk = false
        val sb = StringBuilder()

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val code = c.code

            // Korean Hangul Syllables (0xAC00..0xD7A3)
            if (code in 0xAC00..0xD7A3) {
                hasCjk = true
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

            // Japanese Hiragana & Katakana
            val romaji = getKanaRomaji(c)
            if (romaji != null) {
                hasCjk = true
                sb.append(romaji)
                i++
                continue
            }

            sb.append(c)
            i++
        }

        return if (hasCjk) sb.toString() else null
    }

    private fun getKanaRomaji(c: Char): String? {
        return when (c) {
            // Hiragana
            'あ' -> "a"; 'い' -> "i"; 'う' -> "u"; 'え' -> "e"; 'お' -> "o"
            'か' -> "ka"; 'き' -> "ki"; 'く' -> "ku"; 'け' -> "ke"; 'こ' -> "ko"
            'さ' -> "sa"; 'し' -> "shi"; 'す' -> "su"; 'せ' -> "se"; 'そ' -> "so"
            'た' -> "ta"; 'ち' -> "chi"; 'つ' -> "tsu"; 'て' -> "te"; 'と' -> "to"
            'な' -> "na"; 'に' -> "ni"; 'ぬ' -> "nu"; 'ね' -> "ne"; 'の' -> "no"
            'は' -> "ha"; 'ひ' -> "hi"; 'ふ' -> "fu"; 'へ' -> "he"; 'ほ' -> "ho"
            'ま' -> "ma"; 'み' -> "mi"; 'む' -> "mu"; 'め' -> "me"; 'も' -> "mo"
            'や' -> "ya"; 'ゆ' -> "yu"; 'よ' -> "yo"
            'ら' -> "ra"; 'り' -> "ri"; 'る' -> "ru"; 'れ' -> "re"; 'ろ' -> "ro"
            'わ' -> "wa"; 'を' -> "wo"; 'ん' -> "n"
            'が' -> "ga"; 'ぎ' -> "gi"; 'ぐ' -> "gu"; 'げ' -> "ge"; 'ご' -> "go"
            'ざ' -> "za"; 'じ' -> "ji"; 'ず' -> "zu"; 'ぜ' -> "ze"; 'ぞ' -> "zo"
            'だ' -> "da"; 'ぢ' -> "ji"; 'づ' -> "zu"; 'で' -> "de"; 'ど' -> "do"
            'ば' -> "ba"; 'び' -> "bi"; 'ぶ' -> "bu"; 'べ' -> "be"; 'ぼ' -> "bo"
            'ぱ' -> "pa"; 'ぴ' -> "pi"; 'ぷ' -> "pu"; 'ぺ' -> "pe"; 'ぽ' -> "po"

            // Katakana
            'ア' -> "a"; 'イ' -> "i"; 'ウ' -> "u"; 'エ' -> "e"; 'オ' -> "o"
            'カ' -> "ka"; 'キ' -> "ki"; 'ク' -> "ku"; 'ケ' -> "ke"; 'コ' -> "ko"
            'サ' -> "sa"; 'シ' -> "shi"; 'ス' -> "su"; 'セ' -> "se"; 'ソ' -> "so"
            'タ' -> "ta"; 'チ' -> "chi"; 'ツ' -> "tsu"; 'テ' -> "te"; 'ト' -> "to"
            'ナ' -> "na"; 'ニ' -> "ni"; 'ヌ' -> "nu"; 'ネ' -> "ne"; 'ノ' -> "no"
            'ハ' -> "ha"; 'ヒ' -> "hi"; 'フ' -> "fu"; 'ヘ' -> "he"; 'ホ' -> "ho"
            'マ' -> "ma"; 'ミ' -> "mi"; 'ム' -> "mu"; 'メ' -> "me"; 'モ' -> "mo"
            'ヤ' -> "ya"; 'ユ' -> "yu"; 'ヨ' -> "yo"
            'ラ' -> "ra"; 'リ' -> "ri"; 'ル' -> "ru"; 'レ' -> "re"; 'ロ' -> "ro"
            'ワ' -> "wa"; 'ヲ' -> "wo"; 'ン' -> "n"
            'ガ' -> "ga"; 'ギ' -> "gi"; 'グ' -> "gu"; 'ゲ' -> "ge"; 'ゴ' -> "go"
            'ザ' -> "za"; 'ジ' -> "ji"; 'ズ' -> "zu"; 'ゼ' -> "ze"; 'ゾ' -> "zo"
            'ダ' -> "da"; 'ヂ' -> "ji"; 'ヅ' -> "zu"; 'デ' -> "de"; 'ド' -> "do"
            'バ' -> "ba"; 'ビ' -> "bi"; 'ブ' -> "bu"; 'ベ' -> "be"; 'ボ' -> "bo"
            'パ' -> "pa"; 'ピ' -> "pi"; 'プ' -> "pu"; 'ペ' -> "pe"; 'ポ' -> "po"
            else -> null
        }
    }
}
