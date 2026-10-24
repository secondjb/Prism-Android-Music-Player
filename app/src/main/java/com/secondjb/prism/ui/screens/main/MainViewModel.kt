package com.secondjb.prism.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.core.stats.AggregatedStats
import com.secondjb.prism.core.stats.ListeningStatsRepository
import com.secondjb.prism.core.stats.StatsFilterRange
import com.secondjb.prism.domain.model.ListeningStats
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val stats: ListeningStats = ListeningStats(),
    val aggregatedStats: AggregatedStats = AggregatedStats(),
    val selectedRange: StatsFilterRange = StatsFilterRange.ALL_TIME,
    val recentTracks: List<Track> = emptyList(),
    val quickRecommendations: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(
    private val repository: MediaStoreAudioRepository,
    private val statsRepository: ListeningStatsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        listenForStatsUpdates()
    }

    private fun listenForStatsUpdates() {
        val repo = statsRepository ?: return
        viewModelScope.launch {
            repo.statsUpdateTrigger.collect {
                refreshStatsOnly()
            }
        }
    }

    fun setStatsRange(range: StatsFilterRange) {
        _uiState.update { it.copy(selectedRange = range) }
        viewModelScope.launch {
            refreshStatsOnly()
        }
    }

    private suspend fun refreshStatsOnly() {
        val range = _uiState.value.selectedRange
        val aggregated = statsRepository?.getAggregatedStats(range) ?: AggregatedStats()
        _uiState.update {
            it.copy(
                aggregatedStats = aggregated,
                stats = it.stats.copy(
                    totalTracksPlayed = aggregated.totalTracksPlayed,
                    totalListeningSeconds = aggregated.totalListeningSeconds,
                    topArtist = aggregated.topArtist ?: it.stats.topArtist,
                    topAlbum = aggregated.topAlbum ?: it.stats.topAlbum
                )
            )
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = repository.loadAllTracks()
            val libraryStats = repository.computeStats(tracks)
            val aggregated = statsRepository?.getAggregatedStats(_uiState.value.selectedRange) ?: AggregatedStats()

            // Recommended tracks: prioritize top played songs if available, else shuffle
            val recommendations = if (aggregated.topSongsList.isNotEmpty()) {
                val topTitles = aggregated.topSongsList.map { it.name.lowercase() }
                val topMatching = tracks.filter { it.title.lowercase() in topTitles }
                val remaining = tracks.filterNot { it in topMatching }.shuffled()
                (topMatching + remaining).take(8)
            } else {
                tracks.shuffled().take(6)
            }
            val recent = tracks.take(5)

            val effectiveTotalPlays = if (aggregated.totalTracksPlayed > 0) aggregated.totalTracksPlayed else libraryStats.totalTracksPlayed
            val effectiveTotalListeningSeconds = if (aggregated.totalListeningSeconds > 0) aggregated.totalListeningSeconds else libraryStats.totalListeningSeconds
            val effectiveTopArtist = aggregated.topArtist ?: libraryStats.topArtist
            val effectiveTopAlbum = aggregated.topAlbum ?: libraryStats.topAlbum

            _uiState.update {
                it.copy(
                    stats = libraryStats.copy(
                        totalTracksPlayed = effectiveTotalPlays,
                        totalListeningSeconds = effectiveTotalListeningSeconds,
                        topArtist = effectiveTopArtist,
                        topAlbum = effectiveTopAlbum
                    ),
                    aggregatedStats = aggregated,
                    recentTracks = recent,
                    quickRecommendations = recommendations,
                    isLoading = false
                )
            }
        }
    }
}
