package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs the current user out and clears the active session.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.signOut()
}
