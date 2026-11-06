package com.secondjb.prism.core.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.secondjb.prism.domain.model.DeviceNode
import com.secondjb.prism.domain.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * mDNS discovery manager powered by Android NsdManager.
 * Discovers Prism Windows desktop nodes running on the same local Wi-Fi.
 * Serializes service resolution to prevent NsdManager.FAILURE_ALREADY_ACTIVE.
 */
class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? = try {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (e: Exception) {
        null
    }
    private val serviceType = "_prism._tcp."

    private val _discoveredDevices = MutableStateFlow<List<DeviceNode>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceNode>> = _discoveredDevices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile
    private var isResolving = false

    fun startDiscovery() {
        if (nsdManager == null || discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("prism", ignoreCase = true)) {
                    enqueueServiceResolution(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredDevices.update { list ->
                    list.filterNot { it.name == service.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            // Service discovery may not be available on some networks
        }
    }

    private fun enqueueServiceResolution(serviceInfo: NsdServiceInfo) {
        resolveQueue.add(serviceInfo)
        processNextResolve()
    }

    @Synchronized
    private fun processNextResolve() {
        if (isResolving) return
        val next = resolveQueue.poll() ?: return
        isResolving = true

        try {
            nsdManager?.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    isResolving = false
                    processNextResolve()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    val deviceName = serviceInfo.serviceName

                    if (host != null) {
                        val node = DeviceNode(
                            id = "$host:$port",
                            name = deviceName,
                            type = DeviceType.WINDOWS_DESKTOP,
                            ipAddress = host,
                            port = port
                        )

                        _discoveredDevices.update { current ->
                            if (current.none { it.id == node.id }) current + node else current
                        }
                    }

                    isResolving = false
                    processNextResolve()
                }
            })
        } catch (e: Exception) {
            isResolving = false
            processNextResolve()
        }
    }

    fun stopDiscovery() {
        resolveQueue.clear()
        isResolving = false
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {}
            discoveryListener = null
        }
    }
}
