package com.secondjb.prism.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.domain.model.ListeningStats
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val stats: ListeningStats = ListeningStats(),
    val recentTracks: List<Track> = emptyList(),
    val quickRecommendations: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(
    private val repository: MediaStoreAudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = repository.loadAllTracks()
            val stats = repository.computeStats(tracks)
            val recommendations = tracks.shuffled().take(6)
            val recent = tracks.take(5)

            _uiState.update {
                it.copy(
                    stats = stats,
                    recentTracks = recent,
                    quickRecommendations = recommendations,
                    isLoading = false
                )
            }
        }
    }
}
