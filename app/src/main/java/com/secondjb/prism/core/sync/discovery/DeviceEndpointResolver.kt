package com.secondjb.prism.core.sync.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.sync.transport.ConnectionEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Auto-healing endpoint resolver that finds paired Prism devices when local IPs change (DHCP).
 * 1. Probes the last-known cached IP with a short timeout.
 * 2. Checks active mDNS discoveries.
 * 3. Sweeps local subnet prefixes concurrently with bounded parallelism and prioritized host scanning.
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
        val candidatePorts = listOfNotNull(
            device.port.takeIf { it > 0 },
            9876.takeIf { it != device.port },
            8765.takeIf { it != device.port && it != 9876 }
        )

        for (port in candidatePorts) {
            if (isReachable(device.host, port, timeoutMs = 1200)) {
                Log.d(TAG, "Cached IP ${device.host}:$port is alive and reachable.")
                return@withContext ConnectionEndpoint(device.host, port, device.token)
            }
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

        // 3. Subnet prefix discovery: scan both local Wi-Fi subnet and cached device subnet
        val subnetPrefixes = mutableSetOf<String>()
        val localIp = getLocalWifiIpAddress()
        if (localIp != null && localIp.count { it == '.' } == 3) {
            subnetPrefixes.add(localIp.substringBeforeLast('.'))
        }
        if (device.host.count { it == '.' } == 3) {
            subnetPrefixes.add(device.host.substringBeforeLast('.'))
        }

        val lastKnownHostNum = device.host.substringAfterLast('.', "").toIntOrNull()

        for (prefix in subnetPrefixes) {
            for (port in candidatePorts) {
                val discoveredHost = scanSubnetForPort(prefix, port, lastKnownHostNum)
                if (discoveredHost != null) {
                    Log.i(TAG, "Subnet sweep discovered server for ${device.name} at: $discoveredHost:$port")
                    onIpUpdated?.invoke(discoveredHost)
                    return@withContext ConnectionEndpoint(discoveredHost, port, device.token)
                }
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

    private suspend fun scanSubnetForPort(
        subnetPrefix: String,
        port: Int,
        lastKnownHostNum: Int? = null
    ): String? = coroutineScope {
        // Prioritize: previous host number +/- 15 first, then common DHCP ranges, then remainder
        val allHosts = (1..254).toList()
        val prioritizedHosts = if (lastKnownHostNum != null && lastKnownHostNum in 1..254) {
            val nearby = (maxOf(1, lastKnownHostNum - 15)..minOf(254, lastKnownHostNum + 15)).toList()
            val remaining = allHosts.filterNot { it in nearby }
            nearby + remaining
        } else {
            allHosts
        }

        val foundIp = CompletableDeferred<String>()
        val semaphore = Semaphore(48)

        val sweepJobs = prioritizedHosts.map { hostNum ->
            val targetIp = "$subnetPrefix.$hostNum"
            launch(Dispatchers.IO) {
                if (foundIp.isCompleted) return@launch
                semaphore.withPermit {
                    if (foundIp.isCompleted) return@launch
                    if (isReachable(targetIp, port, timeoutMs = 750)) {
                        foundIp.complete(targetIp)
                    }
                }
            }
        }

        val result = withTimeoutOrNull(2500L) {
            foundIp.await()
        }
        sweepJobs.forEach { it.cancel() }
        result
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
