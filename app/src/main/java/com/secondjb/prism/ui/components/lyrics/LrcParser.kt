package com.secondjb.prism.ui.components.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    private val LRC_LINE_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    fun parse(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val rawLines = content.lines()

        for (line in rawLines) {
            val trimmed = line.trim()
            val match = LRC_LINE_REGEX.matchEntire(trimmed)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val fractionMs = when (fractionStr.length) {
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10
                    else -> fractionStr.toLongOrNull() ?: 0L
                }
                val timestampMs = min * 60_000L + sec * 1_000L + fractionMs
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timestampMs, text))
                }
            }
        }

        // If not in LRC format, treat as plain text with estimated spacing
        if (lines.isEmpty()) {
            rawLines.filter { it.isNotBlank() }.forEachIndexed { index, text ->
                lines.add(LyricLine(index * 4000L, text.trim()))
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
