package com.dermoai.feature.finder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.environment.LocationProvider
import com.dermoai.feature.finder.data.Clinic
import com.dermoai.feature.finder.data.DermatologistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FinderUiState {
    data object Idle : FinderUiState
    data object Locating : FinderUiState
    data object Loading : FinderUiState
    data class Ready(
        val clinics: List<Clinic>,
        val isBroadFallback: Boolean,
        val centerLat: Double,
        val centerLon: Double,
    ) : FinderUiState

    data object NoPermission : FinderUiState
    data object NoLocation : FinderUiState
    data object Empty : FinderUiState
    data class Error(val message: String) : FinderUiState
}

@HiltViewModel
class FinderViewModel @Inject constructor(
    private val repository: DermatologistRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    var state by mutableStateOf<FinderUiState>(FinderUiState.Idle)
        private set

    var selectedClinic by mutableStateOf<Clinic?>(null)
        private set

    var userLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /** Called when the user grants/denies the location permission. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            locateAndSearch()
        } else {
            state = FinderUiState.NoPermission
        }
    }

    /** Resolves the coarse location, then searches nearby. */
    fun locateAndSearch() {
        viewModelScope.launch {
            state = FinderUiState.Locating
            val location = locationProvider.lastCoarseLocation()
            if (location == null) {
                state = FinderUiState.NoLocation
                return@launch
            }
            search(location.first, location.second)
        }
    }

    fun search(lat: Double, lon: Double) {
        viewModelScope.launch {
            state = FinderUiState.Loading
            userLocation = lat to lon
            selectedClinic = null
            runCatching { repository.findNearby(lat, lon) }
                .onSuccess { result ->
                    state = if (result.clinics.isEmpty()) {
                        FinderUiState.Empty
                    } else {
                        FinderUiState.Ready(
                            clinics = result.clinics,
                            isBroadFallback = result.isBroadFallback,
                            centerLat = lat,
                            centerLon = lon,
                        )
                    }
                }
                .onFailure { e ->
                    state = FinderUiState.Error(e.message ?: "Search failed")
                }
        }
    }

    fun selectClinic(clinic: Clinic) {
        selectedClinic = clinic
    }

    fun clearSelection() {
        selectedClinic = null
    }
}
