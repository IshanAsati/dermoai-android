package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.usecase.auth.ObserveAuthStateUseCase
import com.dermoai.core.domain.usecase.auth.ObserveOnboardingUseCase
import com.dermoai.core.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level session + onboarding state for the root navigation guard.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    observeOnboarding: ObserveOnboardingUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    val sessionState: StateFlow<SessionUiState> = combine(
        observeAuthState(),
        observeOnboarding(),
    ) { user, onboarded ->
        SessionUiState(
            isLoading = false,
            isOnboarded = onboarded,
            user = user,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState(isLoading = true),
    )

    fun signOut() {
        viewModelScope.launch { signOutUseCase() }
    }
}

/**
 * Snapshot of auth + onboarding for routing decisions.
 */
data class SessionUiState(
    val isLoading: Boolean = true,
    val isOnboarded: Boolean = false,
    val user: AuthUser? = null,
) {
    val isAuthenticated: Boolean get() = user != null
}
