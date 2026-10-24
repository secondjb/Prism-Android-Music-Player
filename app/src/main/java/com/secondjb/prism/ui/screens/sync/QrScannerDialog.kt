package com.secondjb.prism.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondjb.prism.core.sync.transport.TransportConnectionState

/**
 * Material 3 Expressive pairing dialog supporting live Camera QR scanning,
 * 1-click clipboard paste, and direct IP/Port endpoint entry.
 */
@Composable
fun QrScannerDialog(
    connectionState: TransportConnectionState,
    errorMessage: String?,
    onPair: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var rawInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val initialConnectionState = remember { connectionState }
    var hasInitiatedConnect by remember { mutableStateOf(false) }
    var hasSeenConnecting by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(connectionState) {
        if (connectionState is TransportConnectionState.Connecting) {
            hasSeenConnecting = true
        } else if (connectionState is TransportConnectionState.Connected) {
            val isNewConnection = if (initialConnectionState is TransportConnectionState.Connected) {
                hasInitiatedConnect && hasSeenConnecting
            } else {
                hasInitiatedConnect || hasSeenConnecting
            }
            if (isNewConnection) {
                onDismiss()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            hasInitiatedConnect = false
            hasSeenConnecting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Pair with Prism Windows")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tab Selection: Camera Scanner vs Manual Input
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Camera Scan")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Manual / Paste")
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Live Camera QR Scanner View
                    CameraQrScannerView(
                        onQrCodeScanned = { scannedCode ->
                            if (!hasInitiatedConnect) {
                                hasInitiatedConnect = true
                                rawInput = scannedCode
                                onPair(scannedCode)
                            }
                        }
                    )
                } else {
                    // Manual Input / Paste Tab
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Paste the pairing URI or host:port:token copied from desktop Prism:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = rawInput,
                            onValueChange = { rawInput = it },
                            placeholder = { Text("prism://pair?... or IP:PORT") },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { rawInput = it }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste from Clipboard")
                                }
                            }
                        )

                        // USB ADB Reverse Helper Chip
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "USB Bypass Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap 'Enable ADB Reverse' in desktop modal, then enter: 127.0.0.1:8765",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Connecting Indicator
                if (connectionState is TransportConnectionState.Connecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Connecting to desktop...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Error Message if failed
                if (errorMessage != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1 || rawInput.isNotBlank()) {
                Button(
                    onClick = {
                        if (rawInput.isNotBlank()) {
                            hasInitiatedConnect = true
                            onPair(rawInput.trim())
                        }
                    },
                    enabled = rawInput.isNotBlank() && connectionState !is TransportConnectionState.Connecting
                ) {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
