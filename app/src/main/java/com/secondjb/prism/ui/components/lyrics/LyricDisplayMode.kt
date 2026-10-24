package com.secondjb.prism.ui.components.lyrics

/**
 * Ternary display mode for Romanization and Translation lines in lyrics.
 * OFF: Subline hidden.
 * BELOW: Shown as secondary subline underneath the main line.
 * REPLACE: Replaces the main lyric text.
 */
enum class LyricDisplayMode {
    OFF,
    BELOW,
    REPLACE;

    fun next(): LyricDisplayMode = when (this) {
        OFF -> BELOW
        BELOW -> REPLACE
        REPLACE -> OFF
    }

    companion object {
        fun fromString(value: String): LyricDisplayMode = try {
            valueOf(value.uppercase())
        } catch (_: Exception) {
            OFF
        }
    }
}
