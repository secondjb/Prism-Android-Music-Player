package com.secondjb.prism.ui.screens.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.data.PrismPreferences
import com.secondjb.prism.core.data.PrismPreferencesRepository

/**
 * Categorized settings screen for Prism Android with feature parity to Prism Windows SettingsView.
 */
@Composable
fun SettingsScreen(
    preferencesRepository: PrismPreferencesRepository,
    totalTrackCount: Int = 0,
    onRescanLibrary: () -> Unit = {},
    onScanQrClick: () -> Unit = {},
    connectedDeviceName: String? = null,
    isDesktopConnected: Boolean = false,
    pairedDevices: List<PairedDevice> = emptyList(),
    onConnectToPairedDevice: (PairedDevice) -> Unit = {},
    onDeletePairedDevice: (PairedDevice) -> Unit = {},
    onStartFileSync: () -> Unit = {},
    onSyncStatsNow: () -> Unit = {},
    onClearStatsHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prefs by preferencesRepository.preferences.collectAsState()

    var collapsedLibrary by remember { mutableStateOf(false) }
    var collapsedAudio by remember { mutableStateOf(false) }
    var collapsedLyrics by remember { mutableStateOf(false) }
    var collapsedTheme by remember { mutableStateOf(false) }
    var collapsedSync by remember { mutableStateOf(false) }

    var isSelectingIncludeFolder by remember { mutableStateOf(true) }
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val folderPath = if (docId.contains(':')) docId.substringAfter(':') else (uri.lastPathSegment ?: docId)
            if (folderPath.isNotBlank()) {
                if (isSelectingIncludeFolder) {
                    preferencesRepository.addIncludedFolder(folderPath)
                } else {
                    preferencesRepository.addExcludedFolder(folderPath)
                }
                onRescanLibrary()
            }
        }
    }

    var showAddFolderDialog by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (isIncluded, customText)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // 1. Library & File Discovery
        item {
            SettingsCategoryCard(
                title = "Library & File Discovery",
                subtitle = "Storage scan, audio formats, folder inclusions & exclusions",
                icon = Icons.Default.Folder,
                isCollapsed = collapsedLibrary,
                onToggleCollapse = { collapsedLibrary = !collapsedLibrary }
            ) {
                // Total Tracks & Rescan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Local Audio Library", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("$totalTrackCount audio files indexed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = onRescanLibrary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Included Folders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Included Music Folders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (prefs.includedFolders.isEmpty()) "Scanning all device audio (no restrictions)" else "Only indexing songs within specified folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            isSelectingIncludeFolder = true
                            openDocumentTreeLauncher.launch(null)
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Include")
                    }
                }

                if (prefs.includedFolders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        prefs.includedFolders.forEach { folder ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text(folder) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            preferencesRepository.removeIncludedFolder(folder)
                                            onRescanLibrary()
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Excluded Folders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Excluded Folders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (prefs.excludedFolders.isEmpty()) "No folders excluded" else "Skipping songs in specified folders (e.g. Ringtones, Recordings)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            isSelectingIncludeFolder = false
                            openDocumentTreeLauncher.launch(null)
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Exclude")
                    }
                }

                if (prefs.excludedFolders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        prefs.excludedFolders.forEach { folder ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text(folder) },
                                leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            preferencesRepository.removeExcludedFolder(folder)
                                            onRescanLibrary()
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Minimum Duration Filter
                Text("Minimum Duration Filter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Ignore short notification sounds, ringtones, or voice memos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0 to "All", 10 to "10s", 30 to "30s", 60 to "60s").forEach { (sec, label) ->
                        FilterChip(
                            selected = prefs.minDurationSeconds == sec,
                            onClick = {
                                preferencesRepository.setMinDurationSeconds(sec)
                                onRescanLibrary()
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Enabled Formats
                Text("Supported Audio Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Toggle file formats scanned from device storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("FLAC", "MP3", "M4A", "WAV", "OGG", "OPUS").forEach { fmt ->
                        val isEnabled = prefs.enabledFormats.contains(fmt)
                        FilterChip(
                            selected = isEnabled,
                            onClick = {
                                preferencesRepository.toggleFormat(fmt)
                                onRescanLibrary()
                            },
                            label = { Text(fmt) },
                            leadingIcon = if (isEnabled) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // 2. Audio Engine & Playback
        item {
            SettingsCategoryCard(
                title = "Audio Engine & Playback",
                subtitle = "Gapless playback, ReplayGain, and crossfading",
                icon = Icons.Default.Audiotrack,
                isCollapsed = collapsedAudio,
                onToggleCollapse = { collapsedAudio = !collapsedAudio }
            ) {
                // Gapless playback switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Gapless Playback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Preload next audio track in memory for seamless live transitions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isGaplessEnabled,
                        onCheckedChange = { preferencesRepository.setGaplessEnabled(it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ReplayGain Normalization Mode
                Text("ReplayGain Loudness Normalization", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Align playback loudness across different albums and releases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))

                Column {
                    listOf(
                        "track" to "Track Gain (Recommended individual normalization)",
                        "album" to "Album Gain (Preserves dynamic volume balance across album)",
                        "off" to "Disabled (Raw unadjusted source volume)"
                    ).forEach { (mode, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { preferencesRepository.setReplayGainMode(mode) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = prefs.replayGainMode == mode,
                                onClick = { preferencesRepository.setReplayGainMode(mode) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Crossfade
                Text("Track Crossfade", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Overlap ending of current track with beginning of next", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0 to "Off", 2 to "2 sec", 4 to "4 sec", 6 to "6 sec").forEach { (sec, label) ->
                        FilterChip(
                            selected = prefs.crossfadeDurationSeconds == sec,
                            onClick = { preferencesRepository.setCrossfadeDuration(sec) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // 3. Lyrics & Visualizations
        item {
            SettingsCategoryCard(
                title = "Lyrics & Visualizations",
                subtitle = "Offline-first lyrics, romanization, and wavy seekbar",
                icon = Icons.Default.Mic,
                isCollapsed = collapsedLyrics,
                onToggleCollapse = { collapsedLyrics = !collapsedLyrics }
            ) {
                // Romanization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Romanization & Translation Support", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Show romanized transliterations below non-Latin lyrics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isRomanizationEnabled,
                        onCheckedChange = { /* Persisted */ }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Wavy Seekbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Wavy Audio Seekbar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Sine waveform progress animation inspired by LastWave and Android 13+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isWavySeekbarEnabled,
                        onCheckedChange = { preferencesRepository.setWavySeekbarEnabled(it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // LRCLIB Fetch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Auto-fetch Missing Lyrics (LRCLIB)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Query lrclib.net when local embedded or .lrc files are not found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.lrclibAutoFetch,
                        onCheckedChange = { preferencesRepository.setLrclibAutoFetch(it) }
                    )
                }
            }
        }

        // 4. Themes & Appearance
        item {
            SettingsCategoryCard(
                title = "Themes & Appearance",
                subtitle = "Material 3 Expressive and dynamic album artwork palette",
                icon = Icons.Default.Palette,
                isCollapsed = collapsedTheme,
                onToggleCollapse = { collapsedTheme = !collapsedTheme }
            ) {
                // Dynamic Theming
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Dynamic Album Artwork Palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Extract accent colors from active album art using AndroidX Palette with high text contrast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isDynamicThemingEnabled,
                        onCheckedChange = { preferencesRepository.setDynamicThemingEnabled(it) }
                    )
                }
            }
        }

        // 5. Desktop Companion & LAN Sync
        item {
            SettingsCategoryCard(
                title = "Desktop Companion & LAN Sync",
                subtitle = "Pair with Prism Windows, manage paired PCs & file sync",
                icon = Icons.Default.Sync,
                isCollapsed = collapsedSync,
                onToggleCollapse = { collapsedSync = !collapsedSync }
            ) {
                Text(
                    text = "Seamless offline P2P synchronization with Prism Windows via mDNS and WebSocket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onScanQrClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan QR", maxLines = 1)
                        }
                    }

                    if (isDesktopConnected) {
                        Button(
                            onClick = onStartFileSync,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Smart File Sync", maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))

                // Listening Stats & Analytics Synchronization
                Text("Listening Stats & Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Keep listening history, play counts, and leaderboards identical across PC and phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                // Auto-sync switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Auto-Sync Listening Stats", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Exchange listening events bidirectionally when companion connects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.autoSyncStats,
                        onCheckedChange = { preferencesRepository.setAutoSyncStats(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Prevent double-counting switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Prevent Double-Counting", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Suppress phone play logging when desktop is actively outputting music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.preventDoubleCounting,
                        onCheckedChange = { preferencesRepository.setPreventDoubleCounting(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Record stats switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Record Listening Stats", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Log local listening time and play counts for charts and insights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isStatsCollectionEnabled,
                        onCheckedChange = { preferencesRepository.setStatsCollectionEnabled(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Action buttons: Sync Stats Now & Clear History
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSyncStatsNow,
                        enabled = isDesktopConnected,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Stats Now", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onClearStatsHistory,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Stats", maxLines = 1)
                    }
                }

            }
        }

        // 6. Paired Devices List (Dedicated LazyList section taking only exact space needed)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Paired Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (pairedDevices.isNotEmpty()) {
                    Text(
                        text = "${pairedDevices.size} paired",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (pairedDevices.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "No paired devices yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Scan your desktop QR code to establish seamless pairing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(pairedDevices, key = { it.id }) { device ->
                val isConnected = isDesktopConnected && (connectedDeviceName == device.name)
                PairedDeviceItem(
                    device = device,
                    isConnected = isConnected,
                    onConnect = { onConnectToPairedDevice(device) },
                    onDelete = { onDeletePairedDevice(device) }
                )
            }
        }

        // 6. About
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text("Prism Companion for Android", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Version 1.0.0 • AndroidX Media3 ExoPlayer Engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Add Folder Dialog (Included or Excluded)
    showAddFolderDialog?.let { (isIncluded, _) ->
        var inputPath by remember { mutableStateOf("") }
        val presets = listOf("Music", "Download", "Podcasts", "Audiobooks", "Recordings", "Prism")

        AlertDialog(
            onDismissRequest = { showAddFolderDialog = null },
            title = {
                Text(if (isIncluded) "Include Specific Folder" else "Exclude Folder")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isIncluded) {
                            "Only audio files located within this folder path will be indexed in your library."
                        } else {
                            "Audio files within this folder will be skipped during scanning."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputPath,
                        onValueChange = { inputPath = it },
                        label = { Text("Folder Name or Substring") },
                        placeholder = { Text("e.g. Music, Download, /sdcard/Music") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Quick Presets:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = inputPath.equals(preset, ignoreCase = true),
                                onClick = { inputPath = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPath.isNotBlank()) {
                            if (isIncluded) {
                                preferencesRepository.addIncludedFolder(inputPath.trim())
                            } else {
                                preferencesRepository.addExcludedFolder(inputPath.trim())
                            }
                            onRescanLibrary()
                        }
                        showAddFolderDialog = null
                    },
                    enabled = inputPath.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (isCollapsed) 0f else 180f)
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceItem(
    device: PairedDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device.name.trim().ifBlank { "Prism Desktop" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false) // Leaves room for the badge!
                    )
                    if (isConnected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "CONNECTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${device.host}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isConnected) {
                    FilledTonalButton(
                        onClick = onConnect,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Connect", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Pairing",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
