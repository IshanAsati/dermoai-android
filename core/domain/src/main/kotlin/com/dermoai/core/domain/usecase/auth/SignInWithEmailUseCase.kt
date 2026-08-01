package com.dermoai.core.domain.usecase.auth

import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs in with email and password.
 */
class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<AuthUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            return AppResult.Error(
                IllegalArgumentException("Email and password are required"),
                "Email and password are required",
            )
        }
        return authRepository.signInWithEmail(trimmedEmail, password)
    }
}
