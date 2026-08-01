package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the active authentication session.
 */
class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthUser?> = authRepository.observeAuthState()
}
