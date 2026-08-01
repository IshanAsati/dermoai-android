package com.dermoai.core.domain.repository

import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Authentication contract. Implementations live in [core:data].
 */
interface AuthRepository {
    /** Emits the current session user, or null when signed out. */
    fun observeAuthState(): Flow<AuthUser?>

    suspend fun getCurrentUser(): AuthUser?

    suspend fun signInWithEmail(email: String, password: String): AppResult<AuthUser>

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<AuthUser>

    /**
     * Completes Google Sign-In using an ID token from Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser>

    suspend fun signOut(): AppResult<Unit>

    /** True when Firebase is not provisioned and local session mode is active. */
    fun isLocalAuthMode(): Boolean
}
