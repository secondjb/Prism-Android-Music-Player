package com.secondjb.prism.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondjb.prism.core.playback.SleepTimerMode
import com.secondjb.prism.core.playback.SleepTimerState
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.theme.PillShape
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerModal(
    timerState: SleepTimerState,
    currentQueue: List<Track> = emptyList(),
    currentQueueIndex: Int = -1,
    currentTrackDurationMs: Long = 0L,
    currentTrackPositionMs: Long = 0L,
    onStartMinutes: (Int, Boolean, Boolean) -> Unit,
    onStartTracks: (Int, Boolean, Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isTrackMode by remember { mutableStateOf(timerState.mode == SleepTimerMode.TRACKS) }

    // Defaults: 5 mins for time, 3 tracks for tracks
    var minutesSlider by remember { mutableFloatStateOf(5f) }
    var tracksSlider by remember { mutableFloatStateOf(3f) }

    var finishLastSong by remember { mutableStateOf(timerState.finishLastSong) }
    var playToEndOfQueue by remember { mutableStateOf(timerState.playToEndOfQueue) }

    fun estimateTrackDuration(count: Int): String {
        if (count <= 0) return "0m"
        var totalSecs = 0L
        val currentTrackRemainingSecs = ((currentTrackDurationMs - currentTrackPositionMs).coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)

        if (count == 1) {
            totalSecs = if (currentTrackRemainingSecs > 0) currentTrackRemainingSecs else 210L
        } else {
            totalSecs = if (currentTrackRemainingSecs > 0) currentTrackRemainingSecs else 210L
            val upcomingNeeded = count - 1
            if (currentQueue.isNotEmpty() && currentQueueIndex >= 0) {
                val upcomingTracks = currentQueue.drop(currentQueueIndex + 1).take(upcomingNeeded)
                totalSecs += upcomingTracks.sumOf { it.durationSecs }
                if (upcomingTracks.size < upcomingNeeded) {
                    val missing = upcomingNeeded - upcomingTracks.size
                    totalSecs += missing * 210L
                }
            } else {
                totalSecs += upcomingNeeded * 210L
            }
        }

        val mins = (totalSecs + 30) / 60
        return if (mins < 60) {
            "~${mins}m"
        } else {
            val hrs = mins / 60
            val rem = mins % 60
            if (rem == 0L) "~${hrs}h" else "~${hrs}h ${rem}m"
        }
    }

    val remainingQueueCount = if (currentQueue.isNotEmpty() && currentQueueIndex >= 0) {
        (currentQueue.size - currentQueueIndex - 1).coerceAtLeast(0)
    } else 0
    val queueEstimateStr = estimateTrackDuration(remainingQueueCount + 1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Row: Title, Mode toggle chips, Start button, Dismiss button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isTrackMode) "Track-based countdown" else "Time-based countdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mode Toggle Chips
                    FilterChip(
                        selected = !isTrackMode,
                        onClick = { isTrackMode = false },
                        label = { Text("Mins") },
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = isTrackMode,
                        onClick = { isTrackMode = true },
                        label = { Text("Tracks") },
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    Button(
                        onClick = {
                            if (isTrackMode) {
                                onStartTracks(tracksSlider.roundToInt(), finishLastSong, playToEndOfQueue)
                            } else {
                                onStartMinutes(minutesSlider.roundToInt(), finishLastSong, playToEndOfQueue)
                            }
                            onDismiss()
                        },
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Start", fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // Status Banner if active
            if (timerState.isActive) {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val activeMsg = when (timerState.mode) {
                                SleepTimerMode.TIME -> {
                                    val mins = (timerState.remainingSeconds + 59) / 60
                                    "Stops in $mins min(s)"
                                }
                                SleepTimerMode.TRACKS -> "Stops after ${timerState.remainingTracks} track(s)"
                                SleepTimerMode.END_OF_QUEUE -> "Stops at end of custom queue"
                            }
                            Text(
                                text = "Active Timer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = activeMsg,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (timerState.finishLastSong) {
                                Text(
                                    text = "Will finish current song",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Button(
                            onClick = onCancelTimer,
                            shape = PillShape
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Discrete Slider Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isTrackMode) {
                        val currentTracks = tracksSlider.roundToInt()
                        Text(
                            text = "$currentTracks ${if (currentTracks == 1) "track" else "tracks"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Estimated duration: ${estimateTrackDuration(currentTracks)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        Slider(
                            value = tracksSlider,
                            onValueChange = { tracksSlider = it },
                            valueRange = 1f..20f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1 track", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10 tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("20 tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val currentMins = minutesSlider.roundToInt()
                        val formattedTime = if (currentMins < 60) {
                            "$currentMins minutes"
                        } else {
                            val hrs = currentMins / 60
                            val rem = currentMins % 60
                            if (rem == 0) "$hrs hour${if (hrs > 1) "s" else ""}" else "$hrs hr $rem mins"
                        }

                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Audio will gently turn off after this time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        Slider(
                            value = minutesSlider,
                            onValueChange = { minutesSlider = it },
                            valueRange = 5f..120f,
                            steps = 22, // 5 min increments from 5 to 120: (120-5)/5 - 1 = 22 steps
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("30m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("60m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("120m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Options: Finish Last Song & End of Custom Queue
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Switch 1: Finish Last Song Before Stopping
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { finishLastSong = !finishLastSong }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Finish last song before stopping",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Let current song play out completely before turning off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = finishLastSong,
                            onCheckedChange = { finishLastSong = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Switch 2: Play to End of Custom Queue
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playToEndOfQueue = !playToEndOfQueue }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Play to end of custom queue",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Stop after queue ends ($remainingQueueCount tracks remaining, $queueEstimateStr)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playToEndOfQueue,
                            onCheckedChange = { playToEndOfQueue = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}
