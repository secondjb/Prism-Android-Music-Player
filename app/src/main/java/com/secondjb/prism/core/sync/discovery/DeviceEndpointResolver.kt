package com.secondjb.prism.core.sync.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.sync.transport.ConnectionEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Auto-healing endpoint resolver that finds paired Prism devices when local IPs change (DHCP).
 * 1. Probes the last-known cached IP with a short timeout.
 * 2. Checks active mDNS discoveries.
 * 3. Sweeps the local Wi-Fi /24 subnet on port 9876 concurrently.
 */
class DeviceEndpointResolver(
    private val context: Context,
    private val discoveryManager: NsdDiscoveryManager? = null
) {
    suspend fun resolveEndpoint(
        device: PairedDevice,
        onIpUpdated: (suspend (String) -> Unit)? = null
    ): ConnectionEndpoint? = withContext(Dispatchers.IO) {
        // 1. Quick probe on last known host
        if (isReachable(device.host, device.port, timeoutMs = 1200)) {
            Log.d(TAG, "Cached IP ${device.host}:${device.port} is alive and reachable.")
            return@withContext ConnectionEndpoint(device.host, device.port, device.token)
        }

        Log.i(TAG, "Cached IP ${device.host}:${device.port} unreachable. Starting dynamic LAN auto-healing sweep...")

        // 2. Check mDNS discovered devices
        val mdnsDevices = discoveryManager?.discoveredDevices?.value ?: emptyList()
        val mdnsMatch = mdnsDevices.firstOrNull { matchesDeviceName(it.name, device.name) }
            ?: if (mdnsDevices.size == 1) mdnsDevices.first() else null

        if (mdnsMatch != null && isReachable(mdnsMatch.ipAddress, mdnsMatch.port, timeoutMs = 1200)) {
            Log.i(TAG, "Found ${device.name} via mDNS at updated IP: ${mdnsMatch.ipAddress}:${mdnsMatch.port}")
            onIpUpdated?.invoke(mdnsMatch.ipAddress)
            return@withContext ConnectionEndpoint(mdnsMatch.ipAddress, mdnsMatch.port, device.token)
        }

        // 3. Parallel subnet sweep across /24
        val localIp = getLocalWifiIpAddress()
        if (localIp != null && localIp.count { it == '.' } == 3) {
            val subnetPrefix = localIp.substringBeforeLast('.')
            val discoveredHost = scanSubnetForPort(subnetPrefix, device.port)
            if (discoveredHost != null) {
                Log.i(TAG, "Subnet sweep discovered server for ${device.name} at: $discoveredHost:${device.port}")
                onIpUpdated?.invoke(discoveredHost)
                return@withContext ConnectionEndpoint(discoveredHost, device.port, device.token)
            }
        }

        Log.w(TAG, "Could not resolve live endpoint for ${device.name}.")
        null
    }

    private fun matchesDeviceName(discoveredName: String, pairedName: String): Boolean {
        if (discoveredName.equals(pairedName, ignoreCase = true)) return true
        if (discoveredName.contains(pairedName, ignoreCase = true) || pairedName.contains(discoveredName, ignoreCase = true)) return true
        val normDiscovered = discoveredName.replace("Prism-Windows-", "", ignoreCase = true)
            .replace("Prism Desktop", "", ignoreCase = true)
            .replace(Regex("[()_-]"), "")
            .trim()
        val normPaired = pairedName.replace("Prism-Windows-", "", ignoreCase = true)
            .replace("Prism Desktop", "", ignoreCase = true)
            .replace(Regex("[()_-]"), "")
            .trim()
        if (normDiscovered.isNotBlank() && normPaired.isNotBlank()) {
            if (normDiscovered.equals(normPaired, ignoreCase = true) ||
                normDiscovered.contains(normPaired, ignoreCase = true) ||
                normPaired.contains(normDiscovered, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun scanSubnetForPort(subnetPrefix: String, port: Int): String? = coroutineScope {
        val sweepJobs = (1..254).map { hostNum ->
            val targetIp = "$subnetPrefix.$hostNum"
            async(Dispatchers.IO) {
                if (isReachable(targetIp, port, timeoutMs = 400)) {
                    targetIp
                } else {
                    null
                }
            }
        }

        val results = sweepJobs.awaitAll()
        results.firstOrNull { it != null }
    }

    private fun getLocalWifiIpAddress(): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val linkProps = cm.getLinkProperties(activeNetwork)
                        val wifiIp = linkProps?.linkAddresses?.map { it.address }
                            ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                            ?.hostAddress
                        if (!wifiIp.isNullOrBlank()) {
                            return wifiIp
                        }
                    }
                }
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val sortedIntfs = interfaces.sortedByDescending {
                it.name.contains("wlan", ignoreCase = true) || it.name.contains("wifi", ignoreCase = true)
            }
            for (intf in sortedIntfs) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to determine local Wi-Fi IP address: ${e.message}")
        }
        return null
    }

    companion object {
        private const val TAG = "EndpointResolver"
    }
}
