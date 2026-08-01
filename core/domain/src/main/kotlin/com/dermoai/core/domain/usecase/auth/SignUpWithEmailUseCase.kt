package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Creates an account with email and password.
 */
class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<AuthUser> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            return AppResult.Error(
                IllegalArgumentException("Email and password are required"),
                "Email and password are required",
            )
        }
        if (password.length < 6) {
            return AppResult.Error(
                IllegalArgumentException("Password too short"),
                "Password must be at least 6 characters",
            )
        }
        return authRepository.signUpWithEmail(trimmedEmail, password, trimmedName)
    }
}
