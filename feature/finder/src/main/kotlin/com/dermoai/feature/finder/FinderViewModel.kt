package com.dermoai.feature.finder

import android.util.Log
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
    data object NoPermission : FinderUiState
    data object NoLocation : FinderUiState

    /**
     * The user's position is known, so the map is shown straight away. The clinic
     * search runs on top of it: [searching] while in flight, [searchFailed] when no
     * server could be reached (the map stays usable either way).
     */
    data class Located(
        val lat: Double,
        val lon: Double,
        val clinics: List<Clinic> = emptyList(),
        val isBroadFallback: Boolean = false,
        val searching: Boolean = true,
        val searchFailed: Boolean = false,
    ) : FinderUiState
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
            selectedClinic = null
            state = FinderUiState.Located(lat, lon, searching = true)
            runCatching { repository.findNearby(lat, lon) }
                .onSuccess { result ->
                    state = FinderUiState.Located(
                        lat = lat,
                        lon = lon,
                        clinics = result.clinics,
                        isBroadFallback = result.isBroadFallback,
                        searching = false,
                    )
                }
                .onFailure { e ->
                    Log.w("DermoFinder", "Search failed", e)
                    state = FinderUiState.Located(lat, lon, searching = false, searchFailed = true)
                }
        }
    }

    fun selectClinic(clinic: Clinic) {
        selectedClinic = clinic
    }

    /** Selects by id — used by taps on map markers. */
    fun selectClinicById(id: String) {
        val clinics = (state as? FinderUiState.Located)?.clinics ?: return
        clinics.firstOrNull { it.id == id }?.let { selectedClinic = it }
    }

    fun clearSelection() {
        selectedClinic = null
    }
}
