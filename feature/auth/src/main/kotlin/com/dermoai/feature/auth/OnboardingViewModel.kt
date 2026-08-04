package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.domain.usecase.auth.CompleteOnboardingUseCase
import com.dermoai.core.domain.usecase.auth.ObserveAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Completes first-run onboarding and signals navigation.
 * Persists the collected profile (age, gender, skin type/tone, lifestyle)
 * into [UserProfileDetailsEntity] so the scan rule layer can use it.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val userProfileDetailsDao: UserProfileDetailsDao,
    private val onboardingProfileStore: OnboardingProfileStore,
) : ViewModel() {

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun complete(profile: OnboardingProfile = OnboardingProfile()) {
        viewModelScope.launch {
            // Never block finishing onboarding on a storage hiccup.
            runCatching { persistProfile(profile) }
            completeOnboarding()
            _finished.emit(Unit)
        }
    }

    private suspend fun persistProfile(profile: OnboardingProfile) {
        // Auth state may emit null briefly while loading — wait for the real user.
        val userId = withTimeoutOrNull(5_000) { observeAuthState().first { it != null } }?.id
        if (userId != null) {
            userProfileDetailsDao.upsert(profile.toEntity(userId))
        } else {
            // Onboarding runs BEFORE sign-in, so a first-time user has no account
            // yet. Stage the profile locally; SessionViewModel flushes it to Room
            // as soon as authentication succeeds — otherwise it is silently lost.
            onboardingProfileStore.save(profile)
        }
    }
}
