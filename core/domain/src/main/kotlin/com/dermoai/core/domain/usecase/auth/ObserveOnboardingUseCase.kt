package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes whether the user has completed onboarding.
 */
class ObserveOnboardingUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = userPreferencesRepository.isOnboarded
}
