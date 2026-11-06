package com.secondjb.prism.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.domain.model.DeviceNode

/**
 * Clean device name formatter: strips raw machine hostnames (e.g., "(AHHHHHHHHHHHHHH)")
 * and formats paired devices sequentially as "Prism Desktop", "Prism Desktop 2", etc.
 */
fun formatDeviceDisplayName(rawName: String?, index: Int = 0): String {
    val name = rawName.orEmpty()
    val isDesktop = name.contains("Desktop", ignoreCase = true) ||
            name.contains("Windows", ignoreCase = true) ||
            name.contains("PC", ignoreCase = true) ||
            name.isBlank()
    val base = if (isDesktop) "Prism Desktop" else "Prism Android"
    return if (index <= 0) base else "$base ${index + 1}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSwitcherSheet(
    isDesktopConnected: Boolean,
    isDesktopOutputEnabled: Boolean,
    isLocalOutputEnabled: Boolean,
    connectedServerName: String?,
    connectedEndpointInfo: String?,
    availableDiscoveredDevices: List<DeviceNode> = emptyList(),
    pairedDevices: List<PairedDevice> = emptyList(),
    starredDeviceId: String = "desktop",
    isBluetoothConnected: Boolean = false,
    bluetoothDeviceName: String? = null,
    onSetStarredDevice: (String) -> Unit = {},
    onToggleDesktopOutput: (Boolean) -> Unit,
    onToggleLocalOutput: (Boolean) -> Unit,
    onConnectToDevice: (DeviceNode) -> Unit = {},
    onConnectToPairedDevice: (PairedDevice) -> Unit = {},
    onDeletePairing: (PairedDevice) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onPairQrClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Audio Output Devices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Multi-room synchronization alert banner
            if (isDesktopConnected && isDesktopOutputEnabled && isLocalOutputEnabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Multi-Room Sync Active: Audio outputs simultaneously on your PC and phone in sync.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 1. Connected or Active Desktop Output
            val hasRemoteDevice = isDesktopConnected || pairedDevices.isNotEmpty()
            if (hasRemoteDevice) {
                val desktopActive = isDesktopConnected && isDesktopOutputEnabled
                val isDesktopStarred = starredDeviceId == "desktop"
                val rawDisplayName = if (isDesktopConnected) {
                    connectedServerName ?: "Prism Desktop"
                } else {
                    pairedDevices.firstOrNull()?.name ?: "Prism Desktop"
                }
                val remoteDeviceDisplayName = formatDeviceDisplayName(rawDisplayName, 0)

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (desktopActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isDesktopConnected) {
                                onToggleDesktopOutput(!isDesktopOutputEnabled)
                            } else if (pairedDevices.isNotEmpty()) {
                                onConnectToPairedDevice(pairedDevices.first())
                            } else {
                                onPairQrClick()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = if (desktopActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = remoteDeviceDisplayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isDesktopConnected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            Text(
                                text = if (isDesktopConnected) {
                                    "Connected • ${connectedEndpointInfo ?: "LAN Synced"}"
                                } else {
                                    "Not Connected • Tap to connect"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isDesktopConnected) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        if (isDesktopConnected) {
                            IconButton(
                                onClick = { onDisconnect() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Disconnect from PC",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Star button to designate as queue authority
                        IconButton(
                            onClick = { onSetStarredDevice("desktop") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isDesktopStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isDesktopStarred) "Starred queue authority" else "Set as queue authority",
                                tint = if (isDesktopStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Checkbox(
                            checked = desktopActive,
                            onCheckedChange = { onToggleDesktopOutput(it) },
                            enabled = isDesktopConnected,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // 2. This Phone Device Row (with Bluetooth detection)
            val isLocalStarred = starredDeviceId == "local" || starredDeviceId == "phone"
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isLocalOutputEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleLocalOutput(!isLocalOutputEnabled) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isBluetoothConnected) Icons.Default.BluetoothAudio else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (isLocalOutputEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "This Phone",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBluetoothConnected && !bluetoothDeviceName.isNullOrBlank()) {
                            Text(
                                text = "Bluetooth • $bluetoothDeviceName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Star button to designate as queue authority
                    IconButton(
                        onClick = { onSetStarredDevice("local") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocalStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isLocalStarred) "Starred queue authority" else "Set as queue authority",
                            tint = if (isLocalStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Checkbox(
                        checked = isLocalOutputEnabled,
                        onCheckedChange = { onToggleLocalOutput(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (!hasRemoteDevice && availableDiscoveredDevices.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No other devices connected or found on LAN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 3. Previously Paired Devices (Stored Pairings)
            if (pairedDevices.isNotEmpty()) {
                Text(
                    text = "Paired Devices",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                pairedDevices.forEachIndexed { index, paired ->
                    val isThisCurrentlyConnected = isDesktopConnected && connectedEndpointInfo?.contains(paired.host) == true
                    val formattedName = formatDeviceDisplayName(paired.name, index)

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = if (isThisCurrentlyConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formattedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isThisCurrentlyConnected) "Connected • ${paired.host}:${paired.port}" else "${paired.host}:${paired.port}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isThisCurrentlyConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isThisCurrentlyConnected) {
                                Text(
                                    text = "Connect",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onConnectToPairedDevice(paired) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            // Delete pairing button
                            IconButton(
                                onClick = { onDeletePairing(paired) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Pairing",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Additional Discovered LAN Devices (if any)
            if (availableDiscoveredDevices.isNotEmpty()) {
                Text(
                    text = "Nearby Devices",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                availableDiscoveredDevices.forEachIndexed { index, device ->
                    val formattedName = formatDeviceDisplayName(device.name, index)

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnectToDevice(device) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formattedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${device.ipAddress}:${device.port}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Connect",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Pair / Scan QR Code Button - Same Primary Color as Play Button
            Button(
                onClick = onPairQrClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isDesktopConnected) "Change / Re-scan Pairing QR Code" else "Scan Windows Pairing QR Code"
                )
            }
        }
    }
}
