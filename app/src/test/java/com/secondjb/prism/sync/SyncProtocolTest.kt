package com.secondjb.prism.sync

import com.secondjb.prism.core.sync.protocol.CommandAction
import com.secondjb.prism.core.sync.protocol.SyncFrame
import com.secondjb.prism.core.sync.qr.QrPairingParser
import com.secondjb.prism.domain.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testQrPairingUriParsing() {
        val uri = "prism://pair?host=192.168.1.42&port=9000&token=secret123&name=Studio-Desktop"
        val result = QrPairingParser.parse(uri)

        assertTrue(result.isSuccess)
        val pairing = result.getOrThrow()
        assertEquals("192.168.1.42", pairing.endpoint.host)
        assertEquals(9000, pairing.endpoint.port)
        assertEquals("secret123", pairing.endpoint.authToken)
        assertEquals("Studio-Desktop", pairing.peerName)
    }

    @Test
    fun testQrPairingColonFormatParsing() {
        val raw = "192.168.1.200:8765:987654"
        val result = QrPairingParser.parse(raw)

        assertTrue(result.isSuccess)
        val pairing = result.getOrThrow()
        assertEquals("192.168.1.200", pairing.endpoint.host)
        assertEquals(8765, pairing.endpoint.port)
        assertEquals("987654", pairing.endpoint.authToken)
    }

    @Test
    fun testHandshakeFramesSerialization() {
        val req = SyncFrame.HandshakeRequest(
            clientToken = "tok_77",
            deviceName = "Pixel 9 Pro"
        )
        val encodedReq = json.encodeToString<SyncFrame>(req)
        assertTrue(encodedReq.contains("handshake_req"))
        val decodedReq = json.decodeFromString<SyncFrame>(encodedReq)
        assertEquals(req, decodedReq)

        val resp = SyncFrame.HandshakeResponse(
            isApproved = true,
            serverDeviceName = "Prism Windows",
            sessionId = "sess_42"
        )
        val encodedResp = json.encodeToString<SyncFrame>(resp)
        assertTrue(encodedResp.contains("handshake_resp"))
        val decodedResp = json.decodeFromString<SyncFrame>(encodedResp)
        assertEquals(resp, decodedResp)
    }

    @Test
    fun testCommandAndSwapFramesSerialization() {
        val cmd = SyncFrame.PlaybackCommand(
            action = CommandAction.SEEK,
            seekPositionMs = 45000L
        )
        val encodedCmd = json.encodeToString<SyncFrame>(cmd)
        assertTrue(encodedCmd.contains("command"))
        val decodedCmd = json.decodeFromString<SyncFrame>(encodedCmd)
        assertEquals(cmd, decodedCmd)

        val swap = SyncFrame.SwapDeviceTarget(targetDeviceId = "remote_win_1")
        val encodedSwap = json.encodeToString<SyncFrame>(swap)
        assertTrue(encodedSwap.contains("swap_device"))
        val decodedSwap = json.decodeFromString<SyncFrame>(encodedSwap)
        assertEquals(swap, decodedSwap)
    }

    @Test
    fun testDecodeRealStateSync() {
        val file = java.io.File("../real_state_sync.json")
        if (file.exists()) {
            val content = file.readText()
            val frame = json.decodeFromString<SyncFrame>(content)
            assertTrue(frame is SyncFrame.StateSync)
            val stateSync = frame as SyncFrame.StateSync
            assertNotNull(stateSync.playbackState)
        }
    }
}
