package com.dermoai.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * App preference accessors used by presentation and use cases.
 */
interface UserPreferencesRepository {
    val isOnboarded: Flow<Boolean>
    val activeUserId: Flow<String?>

    suspend fun setOnboarded(value: Boolean)
    suspend fun setActiveUserId(userId: String?)
}
