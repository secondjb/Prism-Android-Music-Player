package com.secondjb.prism.core.sync.qr

import com.secondjb.prism.core.sync.transport.ConnectionEndpoint
import java.net.URI

data class QrPairingResult(
    val endpoint: ConnectionEndpoint,
    val peerName: String
)

object QrPairingParser {

    /**
     * Parses a pairing payload into a ConnectionEndpoint.
     * Supports:
     * 1. URI format: prism://pair?host=192.168.1.50&port=8765&token=123456&name=WindowsPC
     * 2. Host:Port format: 192.168.1.50:8765:123456
     */
    fun parse(rawCode: String): Result<QrPairingResult> = runCatching {
        val trimmed = rawCode.trim()

        if (trimmed.startsWith("prism://pair", ignoreCase = true)) {
            val uri = URI(trimmed)
            val queryParams = uri.query?.split("&")?.associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            } ?: emptyMap()

            val host = queryParams["host"] ?: queryParams["ip"] ?: throw IllegalArgumentException("Missing host in QR code")
            val port = queryParams["port"]?.toIntOrNull() ?: 8765
            val token = queryParams["token"]
            val fp = queryParams["fp"] ?: queryParams["fingerprint"]
            val name = queryParams["name"] ?: "Prism Desktop"

            QrPairingResult(
                endpoint = ConnectionEndpoint(
                    host = host,
                    port = port,
                    authToken = token,
                    fingerprint = fp
                ),
                peerName = name
            )
        } else if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8765
            val token = parts.getOrNull(2)
            QrPairingResult(
                endpoint = ConnectionEndpoint(
                    host = host,
                    port = port,
                    authToken = token
                ),
                peerName = "Prism Desktop"
            )
        } else {
            throw IllegalArgumentException("Unsupported pairing format: $rawCode")
        }
    }
}
