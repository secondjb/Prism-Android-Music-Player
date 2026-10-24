package com.secondjb.prism.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.secondjb.prism.core.stats.StatTopItem
import com.secondjb.prism.core.stats.StatsFilterRange
import com.secondjb.prism.core.stats.StatsMetric
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.screens.main.MainUiState
import com.secondjb.prism.ui.theme.PillShape

private fun formatStatsDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

private enum class LeaderboardCategoryTab {
    ALL,
    TRACKS,
    ARTISTS,
    ALBUMS
}

@Composable
fun StatsScreen(
    uiState: MainUiState,
    allTracks: List<Track>,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onSelectStatsRange: (StatsFilterRange) -> Unit,
    onMetricChange: (StatsMetric) -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = uiState.aggregatedStats
    var selectedCategory by remember { mutableStateOf(LeaderboardCategoryTab.ALL) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with back button
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to home"
                    )
                }
                Column {
                    Text(
                        text = "Listening Stats",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Detailed insights into your listening habits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Time Filter Range Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(StatsFilterRange.values()) { range ->
                    val label = when (range) {
                        StatsFilterRange.ALL_TIME -> "All Time"
                        StatsFilterRange.THIRTY_DAYS -> "30 Days"
                        StatsFilterRange.SEVEN_DAYS -> "7 Days"
                        StatsFilterRange.TODAY -> "Today"
                    }
                    val isSelected = uiState.selectedRange == range
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectStatsRange(range) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // 3. Top Summary Cards (Side by side)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Total Listening Time
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "TOTAL TIME",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = formatStatsDuration(stats.totalListeningSeconds * 1000L),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${stats.totalTracksPlayed} plays logged",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 2: Top Highlight
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "TOP ARTIST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = stats.topArtist ?: "None yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (stats.topSong != null) "Song: ${stats.topSong}" else "Keep listening",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Metric & Category Controls Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LeaderboardCategoryTab.values().forEach { tab ->
                        val label = when (tab) {
                            LeaderboardCategoryTab.ALL -> "All"
                            LeaderboardCategoryTab.TRACKS -> "Tracks"
                            LeaderboardCategoryTab.ARTISTS -> "Artists"
                            LeaderboardCategoryTab.ALBUMS -> "Albums"
                        }
                        val isSel = selectedCategory == tab
                        Surface(
                            shape = PillShape,
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.clickable { selectedCategory = tab }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Metric Toggle (Time vs Plays)
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(1.dp)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        val isTime = uiState.leaderboardMetric == StatsMetric.TIME
                        Surface(
                            shape = PillShape,
                            color = if (isTime) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { onMetricChange(StatsMetric.TIME) }
                        ) {
                            Text(
                                text = "Time",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTime) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = PillShape,
                            color = if (!isTime) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { onMetricChange(StatsMetric.PLAYS) }
                        ) {
                            Text(
                                text = "Plays",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!isTime) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Leaderboard Sections
        // TOP TRACKS SECTION
        if (selectedCategory == LeaderboardCategoryTab.ALL || selectedCategory == LeaderboardCategoryTab.TRACKS) {
            item {
                LeaderboardCard(
                    title = "TOP TRACKS",
                    count = stats.topSongsList.size,
                    icon = Icons.Default.MusicNote
                ) {
                    if (stats.topSongsList.isEmpty()) {
                        Text(
                            text = "No track plays logged in this range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        val displayTracks = if (selectedCategory == LeaderboardCategoryTab.ALL) stats.topSongsList.take(8) else stats.topSongsList
                        displayTracks.forEachIndexed { idx, item ->
                            val matchedTrack = allTracks.firstOrNull {
                                it.title.equals(item.name, ignoreCase = true) &&
                                (item.subtitle == null || it.artist.equals(item.subtitle, ignoreCase = true))
                            }
                            LeaderboardRowItem(
                                rank = idx + 1,
                                title = item.name,
                                subtitle = item.subtitle ?: "Unknown Artist",
                                artworkUri = item.artworkUri ?: matchedTrack?.artworkUri,
                                isCircle = false,
                                primaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) formatStatsDuration(item.listenedMs) else "${item.count} plays",
                                secondaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) "${item.count} plays" else formatStatsDuration(item.listenedMs),
                                onClick = {
                                    if (matchedTrack != null) {
                                        onTrackClick(matchedTrack, allTracks)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // TOP ARTISTS SECTION
        if (selectedCategory == LeaderboardCategoryTab.ALL || selectedCategory == LeaderboardCategoryTab.ARTISTS) {
            item {
                LeaderboardCard(
                    title = "TOP ARTISTS",
                    count = stats.topArtistsList.size,
                    icon = Icons.Default.Person
                ) {
                    if (stats.topArtistsList.isEmpty()) {
                        Text(
                            text = "No artist plays logged in this range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        val displayArtists = if (selectedCategory == LeaderboardCategoryTab.ALL) stats.topArtistsList.take(8) else stats.topArtistsList
                        displayArtists.forEachIndexed { idx, item ->
                            val matchedArt = allTracks.firstOrNull { it.artist.equals(item.name, ignoreCase = true) }?.artworkUri
                            LeaderboardRowItem(
                                rank = idx + 1,
                                title = item.name,
                                subtitle = "Artist",
                                artworkUri = item.artworkUri ?: matchedArt,
                                isCircle = true,
                                primaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) formatStatsDuration(item.listenedMs) else "${item.count} plays",
                                secondaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) "${item.count} plays" else formatStatsDuration(item.listenedMs),
                                onClick = { onGoToArtist(item.name) }
                            )
                        }
                    }
                }
            }
        }

        // TOP ALBUMS SECTION
        if (selectedCategory == LeaderboardCategoryTab.ALL || selectedCategory == LeaderboardCategoryTab.ALBUMS) {
            item {
                LeaderboardCard(
                    title = "TOP ALBUMS",
                    count = stats.topAlbumsList.size,
                    icon = Icons.Default.Album
                ) {
                    if (stats.topAlbumsList.isEmpty()) {
                        Text(
                            text = "No album plays logged in this range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        val displayAlbums = if (selectedCategory == LeaderboardCategoryTab.ALL) stats.topAlbumsList.take(8) else stats.topAlbumsList
                        displayAlbums.forEachIndexed { idx, item ->
                            val matchedArt = allTracks.firstOrNull { it.album.equals(item.name, ignoreCase = true) }?.artworkUri
                            LeaderboardRowItem(
                                rank = idx + 1,
                                title = item.name,
                                subtitle = item.subtitle ?: "Album",
                                artworkUri = item.artworkUri ?: matchedArt,
                                isCircle = false,
                                primaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) formatStatsDuration(item.listenedMs) else "${item.count} plays",
                                secondaryMetric = if (uiState.leaderboardMetric == StatsMetric.TIME) "${item.count} plays" else formatStatsDuration(item.listenedMs),
                                onClick = { onGoToAlbum(item.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(
    title: String,
    count: Int,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (count > 0) "$count total" else "None",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LeaderboardRowItem(
    rank: Int,
    title: String,
    subtitle: String,
    artworkUri: String?,
    isCircle: Boolean,
    primaryMetric: String,
    secondaryMetric: String,
    onClick: () -> Unit
) {
    // Medal styles: 1 = Gold, 2 = Silver, 3 = Bronze, 4+ = Neutral
    val (badgeBg, badgeBorder, badgeTextColor) = when (rank) {
        1 -> Triple(Color(0x33FACC15), Color(0xFFFACC15), Color(0xFFFACC15))
        2 -> Triple(Color(0x22FFFFFF), Color(0x66FFFFFF), Color(0xFFF4F4F5))
        3 -> Triple(Color(0x33F59E0B), Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> Triple(Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.45f))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.03f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Rank Medal + Thumbnail + Title/Artist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            ) {
                // Rank Medal
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeTextColor
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Media Thumbnail
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), if (isCircle) CircleShape else RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isCircle) Icons.Default.Person else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Title + Subtitle with strict ellipsis truncation
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Stat Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = primaryMetric,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = secondaryMetric,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
