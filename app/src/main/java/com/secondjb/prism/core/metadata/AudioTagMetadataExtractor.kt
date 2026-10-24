package com.secondjb.prism.core.metadata

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin audio metadata and lyrics extractor for FLAC, MP3, and MP4/M4A files.
 * Extracts embedded lyrics tags (Vorbis comment SYNCEDLYRICS/LYRICS/USLT, ID3v2 USLT/SYLT, MP4 \xa9lyr)
 * and sidecar .lrc files.
 */
object AudioTagMetadataExtractor {

    private val FLAC_MAGIC = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
    private val ID3_MAGIC = byteArrayOf(0x49, 0x44, 0x33)         // "ID3"

    /**
     * Attempts to find sidecar .lrc or .txt lyrics in the same directory as [audioFile].
     */
    fun findSidecarLyrics(audioFile: File): String? {
        return try {
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            val lrcFile = File(parent, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.isFile && lrcFile.length() > 0) {
                return lrcFile.readText(StandardCharsets.UTF_8).trim().takeIf { it.isNotEmpty() }
            }

            val txtFile = File(parent, "$baseName.txt")
            if (txtFile.exists() && txtFile.isFile && txtFile.length() > 0) {
                val content = txtFile.readText(StandardCharsets.UTF_8).trim()
                if (content.contains("[0") || content.lines().size > 3) {
                    return content
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts embedded lyrics from an audio file on disk, checking sidecar first then embedded tags.
     */
    fun extractLyrics(file: File): String? {
        if (!file.exists() || !file.canRead()) return null

        // 1. Try sidecar .lrc file first
        val sidecar = findSidecarLyrics(file)
        if (!sidecar.isNullOrBlank()) {
            return sidecar
        }

        // 2. Try embedded metadata tags
        return try {
            file.inputStream().use { stream ->
                extractFromStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts embedded lyrics from an [InputStream].
     */
    fun extractFromStream(stream: InputStream): String? {
        val buffered = stream.buffered(65536)
        buffered.mark(16)
        val header = ByteArray(4)
        val read = buffered.read(header)
        buffered.reset()

        if (read < 4) return null

        return when {
            header.contentEquals(FLAC_MAGIC) -> extractFlacVorbisLyrics(buffered)
            header[0] == ID3_MAGIC[0] && header[1] == ID3_MAGIC[1] && header[2] == ID3_MAGIC[2] -> extractId3Lyrics(buffered)
            else -> extractMp4OrGenericLyrics(buffered)
        }
    }

    // =========================================================================
    // FLAC Vorbis Comments Extractor
    // =========================================================================

    private fun extractFlacVorbisLyrics(input: InputStream): String? {
        // Skip "fLaC"
        val magic = ByteArray(4)
        if (input.read(magic) != 4 || !magic.contentEquals(FLAC_MAGIC)) return null

        var bestLyrics: String? = null
        var bestScore = 0

        while (true) {
            val blockHeader = ByteArray(4)
            if (input.read(blockHeader) != 4) break

            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            if (blockType == 4) { // VORBIS_COMMENT
                val data = ByteArray(length)
                var bytesRead = 0
                while (bytesRead < length) {
                    val r = input.read(data, bytesRead, length - bytesRead)
                    if (r <= 0) break
                    bytesRead += r
                }

                if (bytesRead == length) {
                    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    if (buffer.remaining() >= 4) {
                        val vendorLen = buffer.int
                        if (vendorLen in 0..buffer.remaining()) {
                            buffer.position(buffer.position() + vendorLen) // Skip vendor string
                            if (buffer.remaining() >= 4) {
                                val commentCount = buffer.int
                                for (i in 0 until commentCount) {
                                    if (buffer.remaining() < 4) break
                                    val commentLen = buffer.int
                                    if (commentLen < 0 || commentLen > buffer.remaining()) break

                                    val commentBytes = ByteArray(commentLen)
                                    buffer.get(commentBytes)
                                    val commentStr = String(commentBytes, StandardCharsets.UTF_8)
                                    val eqIdx = commentStr.indexOf('=')
                                    if (eqIdx > 0) {
                                        val key = commentStr.substring(0, eqIdx).uppercase()
                                        val value = commentStr.substring(eqIdx + 1).trim()
                                        val score = when (key) {
                                            "SYNCEDLYRICS" -> 10
                                            "LYRICS" -> 8
                                            "UNSYNCEDLYRICS" -> 7
                                            "USLT" -> 5
                                            else -> if (key.contains("SYNCED") && !key.contains("UNSYNCED")) 9 else 0
                                        }
                                        if (score > bestScore && value.isNotBlank()) {
                                            bestScore = score
                                            bestLyrics = value
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Skip other block types
                input.skip(length.toLong())
            }

            if (isLast) break
        }

        return bestLyrics
    }

    // =========================================================================
    // ID3v2 Lyrics Extractor (MP3 / WAV / AIFF)
    // =========================================================================

    private fun extractId3Lyrics(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) return null

        val majorVersion = header[3].toInt() and 0xFF
        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)

        if (tagSize <= 0 || tagSize > 15 * 1024 * 1024) return null

        val tagData = ByteArray(tagSize)
        var totalRead = 0
        while (totalRead < tagSize) {
            val r = input.read(tagData, totalRead, tagSize - totalRead)
            if (r <= 0) break
            totalRead += r
        }

        var offset = 0
        var bestLyrics: String? = null
        var bestScore = 0

        while (offset + 10 <= totalRead) {
            // Read Frame ID (4 ASCII bytes)
            if (tagData[offset] == 0.toByte()) break // padding reached
            val frameId = String(tagData, offset, 4, StandardCharsets.ISO_8859_1)
            offset += 4

            // Frame size
            val frameSize = if (majorVersion >= 4) {
                // ID3v2.4 synchsafe
                ((tagData[offset].toInt() and 0x7F) shl 21) or
                        ((tagData[offset + 1].toInt() and 0x7F) shl 14) or
                        ((tagData[offset + 2].toInt() and 0x7F) shl 7) or
                        (tagData[offset + 3].toInt() and 0x7F)
            } else {
                // ID3v2.3 standard int
                ((tagData[offset].toInt() and 0xFF) shl 24) or
                        ((tagData[offset + 1].toInt() and 0xFF) shl 16) or
                        ((tagData[offset + 2].toInt() and 0xFF) shl 8) or
                        (tagData[offset + 3].toInt() and 0xFF)
            }
            offset += 4
            offset += 2 // skip 2 bytes flags

            if (frameSize <= 0 || offset + frameSize > totalRead) break

            when (frameId) {
                "USLT" -> {
                    // Unsynchronized lyrics frame
                    val lyrics = parseUsltFrame(tagData, offset, frameSize)
                    if (!lyrics.isNullOrBlank() && bestScore < 8) {
                        bestScore = 8
                        bestLyrics = lyrics
                    }
                }
                "SYLT" -> {
                    // Synchronized lyrics frame
                    val lyrics = parseSyltFrame(tagData, offset, frameSize)
                    if (!lyrics.isNullOrBlank() && bestScore < 10) {
                        bestScore = 10
                        bestLyrics = lyrics
                    }
                }
                "TXXX" -> {
                    // Check if TXXX description is LYRICS
                    val (desc, text) = parseTxxxFrame(tagData, offset, frameSize)
                    if (desc.equals("LYRICS", ignoreCase = true) || desc.equals("SYNCEDLYRICS", ignoreCase = true)) {
                        if (!text.isNullOrBlank() && bestScore < 7) {
                            bestScore = 7
                            bestLyrics = text
                        }
                    }
                }
            }

            offset += frameSize
        }

        return bestLyrics
    }

    private fun parseUsltFrame(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 5) return null
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)

        // 3 bytes language
        var cursor = offset + 4 // 1 byte enc + 3 bytes lang

        // Skip descriptor terminated by null
        cursor = skipTerminatedString(data, cursor, offset + size, encodingByte)

        val lyricsLen = (offset + size) - cursor
        if (lyricsLen <= 0) return null

        return try {
            String(data, cursor, lyricsLen, charset).trim().trim('\u0000')
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSyltFrame(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 6) return null
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        // 3 bytes lang, 1 byte time format, 1 byte content type
        var cursor = offset + 6
        // Skip descriptor
        cursor = skipTerminatedString(data, cursor, offset + size, encodingByte)

        // Convert SYLT to LRC timestamps
        val sb = StringBuilder()
        while (cursor < offset + size) {
            val textStart = cursor
            cursor = skipTerminatedString(data, cursor, offset + size, encodingByte)
            val lineText = String(data, textStart, cursor - textStart, charset).trim().trim('\u0000')
            if (cursor + 4 <= offset + size) {
                val timeMs = ((data[cursor].toLong() and 0xFF) shl 24) or
                        ((data[cursor + 1].toLong() and 0xFF) shl 16) or
                        ((data[cursor + 2].toLong() and 0xFF) shl 8) or
                        (data[cursor + 3].toLong() and 0xFF)
                cursor += 4
                val min = timeMs / 60000
                val sec = (timeMs % 60000) / 1000
                val frac = (timeMs % 1000) / 10
                sb.append(String.format("[%02d:%02d.%02d]%s\n", min, sec, frac, lineText))
            } else {
                break
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun parseTxxxFrame(data: ByteArray, offset: Int, size: Int): Pair<String?, String?> {
        if (size < 2) return Pair(null, null)
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val cursor = offset + 1
        val descEnd = skipTerminatedString(data, cursor, offset + size, encodingByte)
        val desc = String(data, cursor, descEnd - cursor, charset).trim().trim('\u0000')
        val textLen = (offset + size) - descEnd
        val text = if (textLen > 0) String(data, descEnd, textLen, charset).trim().trim('\u0000') else null
        return Pair(desc, text)
    }

    private fun skipTerminatedString(data: ByteArray, start: Int, end: Int, encoding: Int): Int {
        var pos = start
        val isTwoByte = encoding == 1 || encoding == 2
        while (pos < end) {
            if (isTwoByte) {
                if (pos + 1 < end && data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    return pos + 2
                }
                pos += 2
            } else {
                if (data[pos] == 0.toByte()) {
                    return pos + 1
                }
                pos++
            }
        }
        return end
    }

    private fun getCharset(encoding: Int): Charset = when (encoding) {
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }

    // =========================================================================
    // MP4 / M4A \xa9lyr Atom Scan
    // =========================================================================

    private fun extractMp4OrGenericLyrics(input: InputStream): String? {
        val buffer = ByteArray(128 * 1024)
        val read = input.read(buffer)
        if (read < 16) return null

        // Look for '\xa9lyr' (0xA9, 0x6C, 0x79, 0x72)
        val needle = byteArrayOf(0xA9.toByte(), 0x6C, 0x79, 0x72)
        for (i in 0 until read - needle.size - 16) {
            var found = true
            for (j in needle.indices) {
                if (buffer[i + j] != needle[j]) {
                    found = false
                    break
                }
            }
            if (found) {
                // Within \xa9lyr, skip to 'data' atom
                val after = i + needle.size
                for (k in after until minOf(after + 32, read - 8)) {
                    if (buffer[k] == 'd'.code.toByte() &&
                        buffer[k + 1] == 'a'.code.toByte() &&
                        buffer[k + 2] == 't'.code.toByte() &&
                        buffer[k + 3] == 'a'.code.toByte()
                    ) {
                        val textStart = k + 12 // Skip 4 bytes 'data' + 4 bytes version/flags + 4 bytes zeroes
                        val atomSize = ((buffer[k - 4].toInt() and 0xFF) shl 24) or
                                ((buffer[k - 3].toInt() and 0xFF) shl 16) or
                                ((buffer[k - 2].toInt() and 0xFF) shl 8) or
                                (buffer[k - 1].toInt() and 0xFF)
                        val textLen = maxOf(0, atomSize - 16)
                        if (textStart + textLen <= read) {
                            return String(buffer, textStart, textLen, StandardCharsets.UTF_8).trim()
                        }
                    }
                }
            }
        }
        return null
    }
}
