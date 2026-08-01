package com.dermoai.core.domain.model

/**
 * Authenticated user identity used across the app.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val isAnonymous: Boolean = false,
    val photoUrl: String? = null,
)
