package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.domain.repository.UserPreferencesRepository
import javax.inject.Inject

/**
 * Marks first-run onboarding as completed.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        userPreferencesRepository.setOnboarded(true)
    }
}
