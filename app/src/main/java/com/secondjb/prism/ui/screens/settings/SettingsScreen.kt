package com.secondjb.prism.ui.screens.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    val prefs by preferencesRepository.preferences.collectAsState()

    var collapsedLibrary by remember { mutableStateOf(false) }
    var collapsedAudio by remember { mutableStateOf(false) }
    var collapsedLyrics by remember { mutableStateOf(false) }
    var collapsedTheme by remember { mutableStateOf(false) }
    var collapsedSync by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 120.dp),
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
                subtitle = "Storage scan, audio formats, and minimum duration",
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
                            onClick = { preferencesRepository.setMinDurationSeconds(sec) },
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
                            onClick = { preferencesRepository.toggleFormat(fmt) },
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
                            Spacer(Modifier.width(6.dp))
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Crossfade
                Text("Crossfade Duration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0 to "Off", 2 to "2s", 4 to "4s", 6 to "6s").forEach { (sec, label) ->
                        FilterChip(
                            selected = prefs.crossfadeDurationSeconds == sec,
                            onClick = { preferencesRepository.setCrossfadeDuration(sec) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // 3. Lyrics & Visuals
        item {
            SettingsCategoryCard(
                title = "Lyrics & Visuals",
                subtitle = "Synced lyrics, LRCLIB auto-fetch, and typography",
                icon = Icons.Default.Mic,
                isCollapsed = collapsedLyrics,
                onToggleCollapse = { collapsedLyrics = !collapsedLyrics }
            ) {
                // Online LRCLIB fallback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Online Lyrics Fallback (LRCLIB)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Automatically query open LRCLIB database when audio file lacks embedded lyrics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.lrclibAutoFetch,
                        onCheckedChange = { preferencesRepository.setLrclibAutoFetch(it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Wavy Seekbar Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("LastWave Animated Waveform", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Fluid animated sine wave seekbar on Now Playing screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.isWavySeekbarEnabled,
                        onCheckedChange = { preferencesRepository.setWavySeekbarEnabled(it) }
                    )
                }
            }
        }

        // 4. Appearance & Dynamic Theming
        item {
            SettingsCategoryCard(
                title = "Appearance & Dynamic Theming",
                subtitle = "Material 3 Expressive palette extraction",
                icon = Icons.Default.Palette,
                isCollapsed = collapsedTheme,
                onToggleCollapse = { collapsedTheme = !collapsedTheme }
            ) {
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
                subtitle = "Pair with Prism Windows over local network",
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
                FilledTonalButton(
                    onClick = onScanQrClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Desktop Pairing QR Code")
                }
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    modifier = Modifier.rotate(if (isCollapsed) -90f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                ) {
                    content()
                }
            }
        }
    }
}
