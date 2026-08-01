package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Completes Google Sign-In with an ID token from Credential Manager.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(idToken: String): AppResult<AuthUser> {
        if (idToken.isBlank()) {
            return AppResult.Error(
                IllegalArgumentException("Missing Google ID token"),
                "Google Sign-In failed. Please try again.",
            )
        }
        return authRepository.signInWithGoogle(idToken)
    }
}
