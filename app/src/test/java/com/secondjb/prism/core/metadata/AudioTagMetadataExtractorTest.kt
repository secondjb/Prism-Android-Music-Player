package com.secondjb.prism.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class AudioTagMetadataExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFindSidecarLrcFile() {
        val audio = tempFolder.newFile("test_track.flac")
        val lrc = File(tempFolder.root, "test_track.lrc")
        lrc.writeText("[00:10.00]Sidecar lyric line 1\n[00:20.00]Sidecar lyric line 2", Charsets.UTF_8)

        val extracted = AudioTagMetadataExtractor.findSidecarLyrics(audio)
        assertNotNull(extracted)
        assertEquals("[00:10.00]Sidecar lyric line 1\n[00:20.00]Sidecar lyric line 2", extracted)
    }

    @Test
    fun testFindSidecarTxtFileWhenLrcAbsent() {
        val audio = tempFolder.newFile("song.mp3")
        val txt = File(tempFolder.root, "song.txt")
        txt.writeText("[00:05.00]Lyrics in txt file\n[00:15.00]Line two", Charsets.UTF_8)

        val extracted = AudioTagMetadataExtractor.findSidecarLyrics(audio)
        assertNotNull(extracted)
        assertEquals("[00:05.00]Lyrics in txt file\n[00:15.00]Line two", extracted)
    }

    @Test
    fun testExtractFlacVorbisCommentLyrics() {
        // Build a mock FLAC stream with Vorbis comment block containing SYNCEDLYRICS
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x66, 0x4C, 0x61, 0x43)) // "fLaC"

        val vorbisPayload = ByteArrayOutputStream()
        // Vendor length (4 bytes little endian) + vendor string
        val vendor = "reference libFLAC 1.4.3".toByteArray(StandardCharsets.UTF_8)
        vorbisPayload.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(vendor.size).array())
        vorbisPayload.write(vendor)

        // Comment count = 1
        vorbisPayload.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array())

        // Comment: "SYNCEDLYRICS=[00:01.00]Hello FLAC"
        val comment = "SYNCEDLYRICS=[00:01.00]Hello FLAC".toByteArray(StandardCharsets.UTF_8)
        vorbisPayload.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(comment.size).array())
        vorbisPayload.write(comment)

        val vorbisBytes = vorbisPayload.toByteArray()

        // Block header: byte 0 = isLast(0x80) | blockType(4) = 0x84
        val blockHeader = byteArrayOf(
            0x84.toByte(),
            ((vorbisBytes.size shr 16) and 0xFF).toByte(),
            ((vorbisBytes.size shr 8) and 0xFF).toByte(),
            (vorbisBytes.size and 0xFF).toByte()
        )
        baos.write(blockHeader)
        baos.write(vorbisBytes)

        val stream = baos.toByteArray().inputStream()
        val lyrics = AudioTagMetadataExtractor.extractFromStream(stream)

        assertNotNull(lyrics)
        assertEquals("[00:01.00]Hello FLAC", lyrics)
    }

    @Test
    fun testExtractId3v2UsltLyrics() {
        // Build mock ID3v2.3 tag with USLT frame
        val framePayload = ByteArrayOutputStream()
        framePayload.write(0) // ISO-8859-1
        framePayload.write("eng".toByteArray(StandardCharsets.ISO_8859_1)) // 3 bytes lang
        framePayload.write(0) // null terminated descriptor
        framePayload.write("Never gonna give you up\nNever gonna let you down".toByteArray(StandardCharsets.ISO_8859_1))

        val frameBytes = framePayload.toByteArray()
        val tagPayload = ByteArrayOutputStream()
        tagPayload.write("USLT".toByteArray(StandardCharsets.ISO_8859_1))
        tagPayload.write(ByteBuffer.allocate(4).putInt(frameBytes.size).array())
        tagPayload.write(byteArrayOf(0, 0)) // 2 bytes flags
        tagPayload.write(frameBytes)

        val tagBytes = tagPayload.toByteArray()

        val fullTag = ByteArrayOutputStream()
        fullTag.write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
        fullTag.write(byteArrayOf(3, 0, 0)) // v2.3, rev 0, flags 0
        // synchsafe tag size
        val s = tagBytes.size
        fullTag.write(byteArrayOf(
            ((s shr 21) and 0x7F).toByte(),
            ((s shr 14) and 0x7F).toByte(),
            ((s shr 7) and 0x7F).toByte(),
            (s and 0x7F).toByte()
        ))
        fullTag.write(tagBytes)

        val stream = fullTag.toByteArray().inputStream()
        val lyrics = AudioTagMetadataExtractor.extractFromStream(stream)

        assertNotNull(lyrics)
        assertEquals("Never gonna give you up\nNever gonna let you down", lyrics)
    }
}
