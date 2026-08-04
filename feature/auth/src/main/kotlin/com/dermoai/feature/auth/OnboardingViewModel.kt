package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.UserProfileDetailsEntity
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
        val userId = withTimeoutOrNull(5_000) { observeAuthState().first { it != null } }?.id ?: return
        userProfileDetailsDao.upsert(
            UserProfileDetailsEntity(
                userId = userId,
                age = profile.age.toIntOrNull()?.coerceIn(1, 120) ?: 0,
                gender = profile.gender,
                skinType = profile.skinType,
                skinTone = profile.skinTone,
                skinConcerns = profile.skinConcerns,
                allergies = profile.allergies,
                medications = profile.medications,
                sunExposure = profile.sunExposure,
                waterIntake = profile.waterIntake,
                sleepHours = profile.sleepHours,
                stressLevel = profile.stressLevel,
                diet = profile.diet,
                smoking = profile.smoking,
                alcohol = profile.alcohol,
                exercise = profile.exercise,
                skinCareRoutine = profile.skinCareRoutine,
                language = profile.language,
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
