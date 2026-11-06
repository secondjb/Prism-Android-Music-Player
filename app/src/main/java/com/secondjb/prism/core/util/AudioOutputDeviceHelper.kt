package com.secondjb.prism.core.util

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors and exposes the active audio output routing (Speaker, Bluetooth, Wired Headset, etc.).
 * Seamlessly detects Bluetooth A2DP, BLE Headset, and SCO connections.
 */
class AudioOutputDeviceHelper(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _bluetoothDeviceName = MutableStateFlow<String?>(null)
    val bluetoothDeviceName: StateFlow<String?> = _bluetoothDeviceName.asStateFlow()

    private var callback: AudioDeviceCallback? = null

    init {
        refreshAudioDevices()
        registerDeviceCallback()
    }

    private fun registerDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            val deviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    refreshAudioDevices()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    refreshAudioDevices()
                }
            }
            callback = deviceCallback
            try {
                audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {}
        }
    }

    fun refreshAudioDevices() {
        val am = audioManager ?: return
        var btFound = false
        var btName: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = try {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            } catch (_: Exception) {
                emptyArray()
            }

            for (device in devices) {
                if (isBluetoothDevice(device)) {
                    btFound = true
                    val prodName = device.productName?.toString()?.trim()
                    if (!prodName.isNullOrBlank()) {
                        btName = prodName
                    }
                    break
                }
            }
        }

        if (!btFound) {
            @Suppress("DEPRECATION")
            if (am.isBluetoothA2dpOn || am.isBluetoothScoOn) {
                btFound = true
                btName = "Bluetooth"
            }
        }

        _isBluetoothConnected.value = btFound
        _bluetoothDeviceName.value = if (btFound) (btName ?: "Bluetooth") else null
    }

    private fun isBluetoothDevice(info: AudioDeviceInfo): Boolean {
        return when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                } else {
                    false
                }
            }
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null && callback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(callback)
            } catch (_: Exception) {}
            callback = null
        }
    }
}
